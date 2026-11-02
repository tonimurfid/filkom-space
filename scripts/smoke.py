"""End-to-end GET/POST smoke against the running local Compose stack (stdlib only)."""
import base64
import datetime as dt
import json
import os
import time
import urllib.error
import urllib.request
import uuid

ROOM = 'http://localhost:8081'
RESERVATION = 'http://localhost:8082'
STUDENT = os.environ.get('DEMO_STUDENT_ID', '235150200111001')


def request(base, path, method='GET', body=None, user=None, expected=200):
    headers = {}
    if user:
        password = os.environ['DEMO_ADMIN_PASSWORD' if user == 'admin' else 'DEMO_STUDENT_PASSWORD']
        token = base64.b64encode(f'{user}:{password}'.encode()).decode()
        headers['Authorization'] = f'Basic {token}'
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        payload = json.load(response)
        assert response.status == expected, (method, path, response.status, payload)
        if expected >= 400:
            assert payload['status'] == expected
            assert all(key in payload for key in ['code', 'message', 'path'])
        if expected == 201:
            assert response.headers['Location'].endswith(payload['id'])
        return payload


def main():
    deadline = time.monotonic() + 90
    while True:
        try:
            request(ROOM, '/api/v1/rooms')
            request(RESERVATION, '/api/v1/reservations', user='admin')
            break
        except (OSError, AssertionError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)
    rooms = request(ROOM, '/api/v1/rooms')
    assert rooms['total'] >= 2, 'Expected simulated seed rooms'
    room = request(ROOM, '/api/v1/rooms', method='POST', user='admin', expected=201, body={
        'code': 'SMOKE-' + uuid.uuid4().hex[:12], 'name': 'Smoke room (simulasi)',
        'building': 'Smoke building (simulasi)', 'floor': 1, 'capacity': 8,
        'facilities': ['WIFI'],
    })
    uuid.UUID(room['id'])
    assert room['isActive'] is True
    assert request(ROOM, '/api/v1/rooms/' + room['id']) == room
    assert request(ROOM, '/api/v1/rooms?minCapacity=8&facility=WIFI')['total'] >= 1
    date = (dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date() + dt.timedelta(days=2)).isoformat()
    slot = f"/api/v1/availability?roomId={room['id']}&date={date}&startTime=13:00&endTime=15:00"
    assert request(RESERVATION, slot)['available'] is True
    body = {'roomId': room['id'], 'studentId': STUDENT, 'date': date,
            'startTime': '13:00', 'endTime': '15:00',
            'purpose': 'Diskusi simulasi smoke', 'participantCount': 6}
    reservation = request(RESERVATION, '/api/v1/reservations', method='POST', body=body,
                          user=STUDENT, expected=201)
    assert reservation['status'] == 'PENDING'
    uuid.UUID(reservation['id'])
    dt.datetime.fromisoformat(reservation['createdAt'])
    assert request(RESERVATION, slot)['available'] is True
    assert request(RESERVATION, '/api/v1/reservations/' + reservation['id'], user=STUDENT) == reservation
    assert request(RESERVATION, '/api/v1/reservations', user=STUDENT)['total'] >= 1
    request(RESERVATION, '/api/v1/reservations', expected=401)
    request(RESERVATION, '/api/v1/reservations?studentId=another-student', user=STUDENT, expected=403)
    request(RESERVATION, '/api/v1/reservations', method='POST', body={**body, 'participantCount': 9}, user=STUDENT, expected=400)
    for base in [ROOM, RESERVATION]:
        for path in ['/swagger-ui/index.html', '/openapi.yaml', '/v3/api-docs']:
            with urllib.request.urlopen(base + path, timeout=10) as response:
                assert response.status == 200
    print('PASS: seven GET/POST operations, HTTP Basic, ownership, capacity, PENDING availability, Swagger')


if __name__ == '__main__':
    main()
