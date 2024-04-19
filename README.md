# Virtual Heart Rate Monitor (for Fitbit Sense and Versa 3)

A pair of apps: one for a Fitbit smart watch, and one for an Android phone, that create a mock Bluetooth heart rate monitor.

This lets you share real time heart rate data from the Fitbit watch with exercise equipment like Peloton, Strava, Zwift, or Wahoo.

### You can help
Google requires testing by a certain number of users in the [closed alpha on Google Play](https://play.google.com/apps/testing/org.noblecow.hrservice) before publishing.<br>
Just drop me an e-mail at [randallndev@gmail.com](mailto:randallndev@gmail.com) to join the test!

![Apps](./apps.jpg)

Currently, the Fitbit smart watches measure heart rate, but do not advertise as generic heart rate monitors and can only maintain one Bluetooth Low Energy connection with the Fibit phone app. As a work around, we'll send heart rate data from the watch to the phone. Then, we'll run an Android app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart rate monitor. Finally, we'll push heart rate data from the Fitbit Android app to our HR Monitor Android app.

Much thanks to the original author, [@mogenson](https://github.com/mogenson/fitbit-heart-rate-service).

For background: I inherited a Fitbit Sense from my wife when she absconded with my Pixel Watch 2.  I was disappointed to discover the lack of heart rate monitor functionality, so I was delighted to discover this app.
I still don't know much about Fitbit watch app development, so hopefully I'll get around to figuring that out and maybe supporting more watches.

## Requirements

1. A Fitbit Sense or Versa 3 smart watch and an Android phone running Android greater or equal to 8.1.
2. Install the Fitbit watch app from the [Fitbit Gallery](https://gallery.fitbit.com/details/6503a799-37f7-43bf-8775-23f1742e2a4e)  
   OR  
   Import the Fitbit app into [Fitbit Studio](https://studio.fitbit.com), start the developer bridge on your watch, and transfer the app.
3. Opt into [closed testing](#you-can-help) on Google Play  
   OR  
   Download and install the APK onto your Android device from the [releases](https://github.com/randalln/fitbit-heart-rate-service/releases) page  
4. If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second device 
on which to run it (since the device with the Fitbit app on it will be running the HR Monitor app and 
playing the role of the heart rate monitor)

## To Use

1. Start the HR Service app on the Fitbit watch.
2. Start the HR Monitor app on the Android device.  
   You should see the BPM value update with each new received value.
3. On your exercise equipment (or app on your second mobile device), search for a Bluetooth heart rate monitor.  
   The mock heart rate monitor will have the same name as the Android device.

### Disclaimer

No implied warranty or guarantee of functionality. The names Fitbit, Android, and Bluetooth are trademarks of their respective owners.

[Privacy Policy](privacy-android.md)
