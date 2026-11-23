"""Real HTTP smoke via the Istio ingress; Python standard library only."""
import base64
import datetime as dt
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('GATEWAY_BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
STUDENT = os.environ.get('DEMO_STUDENT_ID', '235150200111001')
RESULTS = []


def api(path, method='GET', body=None, user=None, expected=200, host=None):
    headers = {}
    if host:
        headers['Host'] = host
    if user:
        variable = 'DEMO_ADMIN_PASSWORD' if user == 'admin' else 'DEMO_STUDENT_PASSWORD'
        token = base64.b64encode(f'{user}:{os.environ[variable]}'.encode()).decode()
        headers['Authorization'] = 'Basic ' + token
    data = None if body is None else json.dumps(body).encode()
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read()
        assert response.status == expected, (method, path, response.status, raw.decode())
        RESULTS.append({'method': method, 'path': path, 'host': host,
                        'expected': expected, 'actual': response.status, 'result': 'PASS'})
        print(f'PASS: {method} {path} -> {response.status}', flush=True)
        if expected == 204:
            assert raw == b'', raw
            return None
        result = json.loads(raw)
        if expected >= 400:
            assert result['status'] == expected
            assert all(k in result for k in ('code', 'message', 'path'))
        if expected == 201:
            assert response.headers['Location'].endswith(result['id'])
        return result


def main():
    api('/api/v1/rooms')
    api('/api/v1/reservations', user='admin')
    api('/api/v2/rooms')
    room_body = {'code': 'MESH-' + uuid.uuid4().hex[:12], 'name': 'Mesh demo',
                 'building': 'Simulasi', 'floor': 1, 'capacity': 8, 'facilities': ['WIFI']}
    room = api('/api/v2/rooms', 'POST', room_body, 'admin', 201)
    rp = '/api/v2/rooms/' + room['id']
    changed = api(rp, 'PUT', {**room_body, 'capacity': 10, 'isActive': False}, 'admin')
    assert changed['capacity'] == 10 and changed['isActive'] is False
    api(rp, 'PUT', room_body, 'admin', 400)
    api(rp + '/status', 'PATCH', {'isActive': True}, STUDENT, 403)
    assert api(rp + '/status', 'PATCH', {'isActive': True}, 'admin')['isActive'] is True
    assert api(rp)['capacity'] == 10
    date = (dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date() + dt.timedelta(days=7)).isoformat()
    slot = f"/api/v2/availability?roomId={room['id']}&date={date}&startTime=13:00&endTime=15:00"
    assert api(slot)['available'] is True  # Reservation -> Room over the mesh.
    body = {'roomId': room['id'], 'studentId': STUDENT,
            'schedule': {'date': date, 'startTime': '13:00', 'endTime': '15:00'},
            'purpose': 'Mesh smoke', 'participantCount': 6}
    booking = api('/api/v2/reservations', 'POST', body, STUDENT, 201)
    bp = '/api/v2/reservations/' + booking['id']
    assert api('/api/v2/reservations', user=STUDENT)['total'] >= 1
    assert api(bp, user=STUDENT)['schedule'] == body['schedule']
    api('/api/v2/reservations', expected=401)
    update = {k: v for k, v in body.items() if k != 'studentId'}
    update['purpose'] = 'Mesh updated'
    replaced = api(bp, 'PUT', update, STUDENT)
    assert replaced['purpose'] == 'Mesh updated' and replaced['schedule'] == body['schedule']
    api(bp + '/decision', 'PUT', {'status': 'APPROVED'}, STUDENT, 403)
    for _ in range(2):
        assert api(bp + '/decision', 'PUT', {'status': 'APPROVED'}, 'admin')['status'] == 'APPROVED'
    api(bp + '/decision', 'PUT', {'status': 'REJECTED'}, 'admin', 409)
    assert api(slot)['available'] is False
    assert api(rp, 'DELETE', user='admin', expected=409)['code'] == 'ROOM_HAS_RESERVATIONS'
    assert api(bp, 'PATCH', {'status': 'CANCELLED'}, STUDENT)['status'] == 'CANCELLED'
    api(bp, 'DELETE', expected=401)
    for _ in range(2):
        api(bp, 'DELETE', user=STUDENT, expected=204)
    assert api(bp, user=STUDENT)['status'] == 'CANCELLED'
    assert api(slot)['available'] is True
    for _ in range(2):
        api(rp, 'DELETE', user='admin', expected=204)
    api(rp, expected=404)
    # Host routing exposes each service's own Swagger/OpenAPI without rewrites.
    for host in ('room.filkom.local', 'reservation.filkom.local'):
        schema = api('/v3/api-docs', host=host)
        assert 'openapi' in schema
    destination = Path(os.environ.get('EVIDENCE_DIR', 'evidence'))
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'gateway-results.json').write_text(json.dumps({
        'timestamp': dt.datetime.now(dt.timezone.utc).isoformat(),
        'gateway': BASE, 'requests': len(RESULTS), 'failed': 0, 'results': RESULTS,
    }, indent=2))
    print(f'PASS: {len(RESULTS)} HTTP requests through Istio ingress; v1/v2, GET/POST/PUT/PATCH/DELETE')


if __name__ == '__main__':
    main()
