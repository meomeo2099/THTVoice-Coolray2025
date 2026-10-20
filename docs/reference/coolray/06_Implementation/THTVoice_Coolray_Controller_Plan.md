# Đề xuất module THTVoice Coolray

## Module
- `CoolrayModelDetector`
- `CoolrayPropertyClient`
- `CoolrayUdsBridge`
- `CoolrayHvacController`
- `CoolrayBodyController`
- `CoolraySeatController`
- `CoolrayStateVerifier`

## Luồng command an toàn
```text
Voice intent
 -> validate model / capability
 -> read current state
 -> stationary/safety guard where relevant
 -> send VHAL direct (preferred) or UDS fallback
 -> wait for state event / read-back
 -> confirm success only if target state matches
```

## Thứ tự test đề xuất
1. Read-only: temperature, fan state, window position, door state.
2. HVAC set temperature / fan.
3. Seat heating/ventilation.
4. Screen/interior lights.
5. Windows/sunroof with stop/read-back.
6. Lock/unlock, mirrors.
7. Trunk only after stationary-state guard is proven.

## Logging tối thiểu
Log: timestamp, model string, property hex, area, requested value, old value, new/read-back value, result/exception.
Không log VIN, activation key, token hoặc dữ liệu người dùng.
