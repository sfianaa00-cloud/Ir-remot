# IR Remote (for ILK-M50Z1SA and other NEC-style TVs)

Android app that sends infrared codes through the phone's built-in IR blaster
(`ConsumerIrManager`).

## Important limits

- **The phone needs an IR emitter.** Most recent phones don't have one. The app shows a
  warning if it can't find one. Without it, use a USB-C or audio-jack IR dongle (not supported by this app).
- **Button codes for the ILK-M50Z1SA could not be found online**, so none are hard-coded.
  The Setup tab helps you find them (below).
- The code has not been compiled or run on a device. Open it in Android Studio and build.

## Download the APK

Go to the repo's **Releases** page and download `IRRemote.apk` from "IR Remote (latest build)". Open it on your Android phone and allow installing from your browser when asked.

## Build

1. Android Studio (Koala or newer) -> Open -> select this `IRRemote` folder.
2. Let Gradle sync, connect a phone with an IR blaster (USB debugging on), press Run.

## Find the codes for your TV

1. Setup tab -> leave address at `00` (or try other values), protocol NEC.
2. Point the phone's IR window (usually top edge) at the TV, about 1 to 2 m away.
3. Press **Send+Next** repeatedly. When the TV reacts (e.g. turns off), press Prev, then Send to confirm.
4. Choose the key (e.g. Power) and press **Save**. Repeat for the other keys.
5. If nothing reacts across 0 to 255, change the address (try `04`, `40`, `20`, `10`, `01`, `80`) or switch on
   Extended NEC. Some TVs use other protocols (RC5, Samsung, etc.); this app only does NEC.

## Better option if you still have the original remote

Capture its real signals with an IR receiver module (e.g. VS1838B) on an Arduino/ESP32 running IRremote's
`ReceiveDump` example. It prints the protocol, address and command for every button.
Enter those values in the Setup tab. If the TV isn't NEC, add its protocol to `IrSender.kt`.

## Layout

- `IrSender.kt` - NEC pulse encoder + `ConsumerIrManager` wrapper
- `MainActivity.kt` - Remote tab, Setup tab (address, command stepper, key assignment), saved in SharedPreferences
