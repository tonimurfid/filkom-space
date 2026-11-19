"""Exercise the API v2 contract against the running Compose stack."""
import datetime as dt
import uuid
from smoke import ROOM, RESERVATION, STUDENT, request, main as smoke_v1


def main():
    # Wait for both applications and verify v1 alongside v2.
    smoke_v1()
    room_body = {
        "code": "V2-" + uuid.uuid4().hex[:12],
        "name": "V2 room", "building": "Simulasi", "floor": 1,
        "capacity": 8, "facilities": ["WIFI"],
    }
    room = request(ROOM, "/api/v2/rooms", "POST", room_body, "admin", 201)
    room_path = "/api/v2/rooms/" + room["id"]
    replacement = {**room_body, "capacity": 10, "isActive": False}
    room = request(ROOM, room_path, "PUT", replacement, "admin")
    assert room["capacity"] == 10 and room["isActive"] is False
    room = request(ROOM, room_path + "/status", "PATCH", {"isActive": True}, "admin")
    assert room["isActive"] is True
    assert request(ROOM, room_path)["id"] == room["id"]
    assert request(ROOM, "/api/v2/rooms?minCapacity=10")["total"] >= 1

    date = (dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()
            + dt.timedelta(days=4)).isoformat()
    schedule = {"date": date, "startTime": "13:00", "endTime": "15:00"}
    body = {"roomId": room["id"], "studentId": STUDENT, "schedule": schedule,
            "purpose": "V2 smoke", "participantCount": 6}
    slot = (f"/api/v2/availability?roomId={room['id']}&date={date}"
            "&startTime=13:00&endTime=15:00")
    assert request(RESERVATION, slot)["schedule"] == schedule
    reservation = request(RESERVATION, "/api/v2/reservations", "POST", body, STUDENT, 201)
    path = "/api/v2/reservations/" + reservation["id"]
    update = {key: value for key, value in body.items() if key != "studentId"}
    update["purpose"] = "V2 revised"
    assert request(RESERVATION, path, "PUT", update, STUDENT)["purpose"] == "V2 revised"
    assert request(RESERVATION, path, user=STUDENT)["schedule"] == schedule
    assert request(RESERVATION, "/api/v2/reservations", user=STUDENT)["total"] >= 1
    decision = {"status": "APPROVED"}
    assert request(RESERVATION, path + "/decision", "PUT", decision, "admin")["status"] == "APPROVED"
    assert request(RESERVATION, path + "/decision", "PUT", decision, "admin")["status"] == "APPROVED"
    assert request(RESERVATION, slot)["available"] is False
    request(ROOM, room_path, "DELETE", user="admin", expected=409)
    assert request(RESERVATION, path, "PATCH", {"status": "CANCELLED"}, STUDENT)["status"] == "CANCELLED"
    assert request(RESERVATION, slot)["available"] is True
    empty_delete(RESERVATION, path, STUDENT)
    empty_delete(RESERVATION, path, STUDENT)
    empty_delete(ROOM, room_path, "admin")
    empty_delete(ROOM, room_path, "admin")
    request(ROOM, room_path, expected=404)
    print("PASS: API v2 GET/POST/PUT/PATCH/DELETE, schedule, permissions, conflict and idempotence")


def empty_delete(base, path, user):
    # The shared request helper expects JSON, while HTTP 204 has an empty body.
    import base64
    import os
    import urllib.request
    password = os.environ["DEMO_ADMIN_PASSWORD" if user == "admin" else "DEMO_STUDENT_PASSWORD"]
    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    req = urllib.request.Request(base + path, method="DELETE",
                                 headers={"Authorization": "Basic " + token})
    with urllib.request.urlopen(req, timeout=10) as response:
        assert response.status == 204
        assert response.read() == b""


if __name__ == "__main__":
    main()
