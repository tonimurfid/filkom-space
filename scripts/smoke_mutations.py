"""End-to-end PUT/PATCH/DELETE smoke through both real Spring services (Python stdlib)."""
import base64
import datetime as dt
import json
import os
import urllib.error
import urllib.request
import uuid

ROOM = "http://localhost:8081"
BOOK = "http://localhost:8082"
STUDENT = os.environ.get("DEMO_STUDENT_ID", "235150200111001")


def api(base, path, method="GET", body=None, role=None, expected=200):
    headers = {}
    if role:
        name = "admin" if role == "admin" else STUDENT
        pwd = os.environ["DEMO_ADMIN_PASSWORD" if role == "admin" else "DEMO_STUDENT_PASSWORD"]
        headers["Authorization"] = "Basic " + base64.b64encode(f"{name}:{pwd}".encode()).decode()
    raw = json.dumps(body).encode() if body is not None else None
    if raw is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, headers=headers, data=raw, method=method)
    try:
        res = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as exc:
        res = exc
    with res:
        assert res.status == expected, (method, path, res.status, res.read().decode())
        if expected == 204:
            assert not res.read()
            return None
        payload = json.load(res)
        if expected >= 400:
            assert payload["status"] == expected, payload
        return payload


def main():
    code = "MUT-" + uuid.uuid4().hex[:12]
    room = api(ROOM, "/api/v1/rooms", "POST", {
        "code": code, "name": "Mutation room", "building": "Simulasi",
        "floor": 2, "capacity": 8, "facilities": ["WIFI"],
    }, "admin", 201)
    rid = room["id"]
    replaced = api(ROOM, "/api/v1/rooms/" + rid, "PUT", {
        "code": code, "name": "Mutation updated room", "building": "Simulasi",
        "floor": 3, "capacity": 10, "facilities": ["PROYEKTOR"],
    }, "admin")
    assert replaced["floor"] == 3 and replaced["capacity"] == 10
    assert replaced["facilities"] == ["PROYEKTOR"]

    inactive = api(ROOM, "/api/v1/rooms/" + rid + "/status", "PATCH",
                   {"isActive": False}, "admin")
    assert inactive["isActive"] is False
    api(ROOM, "/api/v1/rooms/" + rid + "/status", "PATCH", {"isActive": True}, "admin")

    date = (dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()
            + dt.timedelta(days=3)).isoformat()
    payload = {"roomId": rid, "studentId": STUDENT, "date": date, "startTime": "13:00",
               "endTime": "15:00", "purpose": "Diskusi akademik", "participantCount": 6}
    first = api(BOOK, "/api/v1/reservations", "POST", payload, "student", 201)
    fid = first["id"]
    approval = api(BOOK, "/api/v1/reservations/" + fid, "PATCH",
                   {"status": "APPROVED", "reason": "Kapasitas sesuai"}, "admin")
    assert approval["status"] == "APPROVED"
    api(BOOK, "/api/v1/reservations", "POST", payload, "student", 409)
    api(ROOM, "/api/v1/rooms/" + rid, "DELETE", role="admin", expected=409)
    cancelled = api(BOOK, "/api/v1/reservations/" + fid, "PATCH",
                    {"status": "CANCELLED"}, "student")
    assert cancelled["status"] == "CANCELLED"
    second = api(BOOK, "/api/v1/reservations", "POST", payload, "student", 201)
    api(ROOM, "/api/v1/rooms/" + rid, "DELETE", role="admin", expected=409)
    api(BOOK, "/api/v1/reservations/" + second["id"], "PATCH",
        {"status": "CANCELLED"}, "student")
    api(ROOM, "/api/v1/rooms/" + rid, "DELETE", role="admin", expected=204)
    api(ROOM, "/api/v1/rooms/" + rid, "DELETE", role="admin", expected=204)
    api(ROOM, "/api/v1/rooms/" + rid, expected=404)
    print("PASS: PUT/PATCH/DELETE, approval, conflict, cancellation, archive and idempotence")


if __name__ == "__main__":
    main()
