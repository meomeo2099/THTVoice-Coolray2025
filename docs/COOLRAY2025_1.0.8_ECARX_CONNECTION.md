# 1.0.8 (9): connect the OEM CarImpl before getting ICarFunction

## Observed failure

The owner's 1.0.7 diagnostic photo shows both window requests rejected on all four
attempts: MCU group, float position, integer position, open switch. Standard VHAL
is connected and all listed permissions are granted. This is not proof that the
separate OEM client's connection exists.

The earlier capture `20261111-113241-c5382b/log-dieu-hoa-kinh.txt`, lines 220–224,
contains a previously missed message from VehicleSignalManager immediately after
constructing the OEM client: the caller has invoked ICar without connecting first,
and consequently all ICar methods will fail. The subsequent app probe merely
reports available reflection methods, not an established OEM service connection.

## Shipped framework evidence

- AdapterAPI.jar classes.dex SHA256:
  `3ed726200360bf0e9fa1ff359f5d555ecd744463fe8245144be24b3de64e5f03`.
  Craft Anything `android_method_show` evidence
  `ev_05d088ae06d156948ed63ea7b175fb43d2be1e49cdcf48cf88b32f33f5b0bb20`:
  Car.create only reflects CarImpl(Context); it does not call connect.
- arm64/boot-AdapterAPIImpl.vdex SHA256:
  `66e9da5395837fd773ef0f0ab544b54c3362ba8960294d5572e4e8b89a5b9c14`.
  CompactDex class-data/code-item inspection finds public inherited methods on
  VehicleSignalManager: connect `0x1191a`, connectToAutoCarService `0x11926`,
  isConnected `0x11202`, getCarPropertyManager `0x11182`, and
  registerConnectWatcher `0x11a30`. connectToAutoCarService invokes the private
  Android Car client's connect if it is not already connected/connecting.
- CarImpl.checkIfConnectCarService `0x407a` contains the exact warning seen in the
  capture. getICarFunction `0x3fd4` calls this check and then obtains a function
  singleton, even when not ready; the warning does not throw an exception.
- CarFunctionImpl.getInstance `0x6dde` constructs its singleton only once and
  retains the supplied VehicleSignalManager. Recreating CarImpl on a voice-service
  restart would leave that singleton referring to the first manager.

These are static observations plus a matching runtime warning. Physical actuator
success after the fix remains UNVERIFIED.

## Fix

EcarxCarConnection resolves inherited public connect/isConnected/manager methods,
connects the OEM client and polls readiness for at most five seconds between
vendor calls on the command worker. Only then may fromCar invoke getICarFunction.
Timeout/interruption disconnect the partial client; interruption is preserved.
Already ready clients are reused. The successful facade is retained for the
process lifetime with application context, matching the OEM singleton lifetime.

Diagnostic history adds ECARX CONNECT READY only after this sequence completes.
Connection failure still records the actual exception. VHAL and OEM readiness
are separate; a successful method-resolution probe no longer precedes connection.

## Regression verification

A lifecycle-accurate fake refuses getICarFunction before its manager is ready.
The production fromCar path failed this test before the fix and passes afterward.
Tests cover asynchronous readiness, already connected reuse, timeout cleanup and
interruption cleanup. The local targeted suite also reruns HVAC/window and
suggestion regressions. Full Android compilation and unit tests run in the signed
candidate workflow.

Install 1.0.8 over 1.0.7; a normal Android package update restarts the app process.
With the car parked, retry a single driver-window command and a temperature
command. If either still fails, photograph API history after that command. Do not
claim hardware success until the owner observes motion/state change.
