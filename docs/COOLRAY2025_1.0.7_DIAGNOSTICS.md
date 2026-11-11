# Coolray 2025 1.0.7 (8) candidate

## Evidence from 1.0.6 on 11 November 2026

The owner's capture `20261111-113241-c5382b.zip` reports a successful ECARX probe,
correctly parsed window commands, and VHAL temperature writes accepted without a
matching state change. OEM HvacModule reports 22 degrees while standard VHAL keeps
returning 16 degrees. The diagnostic photo additionally shows `CONNECTED: IO_OK`,
UID 1000 and all five listed car permissions granted, with two earlier connection
timeouts. Those historical timeouts do not establish the cause of current actuator
failure. Raw captures remain outside the source repository.

## Candidate changes

- HVAC reads and writes use ICarFunction through EcarxHvacPropertyAccess; driver
  and passenger temperatures map to zones 1 and 4. Global HVAC controls use zone 0.
  Mapping is from COOLRAY2025_VOICE_SPEC.md. Unknown OEM temperature stays unknown;
  it does not fall back to the stale standard VHAL value. Relative temperature
  commands retain the owner's 0.5-degree default.
- Window position retains MCU and customize paths and adds the integer
  setFunctionValue fallback implemented in shipped BCMModule at CompactDex code
  offset 0x63c2 (see the workspace launcher/evidence/WindowVhal.md). Integer current
  position can verify read-back. Zones remain 16, 32, 256, 512. No raw function ID
  is sent as an Android Car property ID.
- ECARX diagnostic history records missing methods, inactive functions, rejected
  requests and accepted-but-unverified requests, including function/property and
  zone. Exceptions and request results also reach logcat. Request acceptance never
  means that a window or HVAC actuator has completed the operation.
- Suggestion lookup scores phrases before parsing candidates. It parses only
  candidates close enough to affect the result, preserves ambiguity rejection,
  checks session cancellation and uses a 750 ms computation budget between calls.
  The capture's approximately 72-second suggestion path no longer needs to parse
  the entire catalog and reload aliases for every distant phrase.

## Validation and limits

Local targeted JVM tests cover failure diagnostics, OEM HVAC routing and stale
VHAL isolation, 22 to 22.5-degree commands, unverified read-back, integer window
fallback, cancellation and suggestion parse-call count. The local suggestion
harness injects its parser; the full parser and Android service require CI.

All new actuator routes remain REFERENCE/UNVERIFIED until tested on the car.
Install the candidate over 1.0.6, try one temperature command and one individual
window command, and reopen Connection diagnostics if either fails. Photograph
the new API history; no clipboard transfer or computer connection is required.
