# Virtual Heart Rate Monitor (for Fitbit Sense and Versa 3)

A pair of apps: one for a Fitbit smart watch, and one for an Android phone, that create a mock Bluetooth heart rate monitor.

This lets you share real time heart rate data from the Fitbit watch with exercise equipment like Peloton, Strava, Zwift, or Wahoo.

[@cwcpers](https://github.com/cwcpers/versa2-heart-rate-service?tab=readme-ov-file) has gotten the 
[watch app](https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124?key=c05ef0f4-c8f7-4459-94f7-50a209108fc6)
working on OS 4 devices too! (Versa 2, Versa Lite, Versa, Ionic)

![Apps](./apps.jpg)

Currently, the Fitbit smart watches measure heart rate, but do not advertise as generic heart rate monitors and can only maintain one Bluetooth Low Energy connection with the Fibit phone app. As a work around, we'll send heart rate data from the watch to the phone. Then, we'll run an Android app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart rate monitor. Finally, we'll push heart rate data from the Fitbit Android app to our HR Monitor Android app.

Much thanks to the original author, [@mogenson](https://github.com/mogenson/fitbit-heart-rate-service).

## Requirements

1. A Fitbit Sense or Versa 3 smart watch and an Android phone running Android greater or equal to 9.0.
2. Install the "HR Service" Fitbit watch app from the [Fitbit Gallery](https://gallery.fitbit.com/details/6503a799-37f7-43bf-8775-23f1742e2a4e).  
   OR  
   Build and run the code using the [Fitbit CLI](https://dev.fitbit.com/getting-started/), start the developer bridge on your watch, and transfer the app.
3. Install Virtual Heart Rate Monitor from Google Play.
4. If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second device 
on which to run it, since the device with the Fitbit app on it will also be running the HR Monitor app and 
playing the role of the heart rate monitor.

[<img src="GetItOnGooglePlay_Badge_Web_color_English.png">](https://play.google.com/store/apps/details?id=org.noblecow.hrservice)

## To Use

1. Start the "HR Service" app on the Fitbit watch.
2. Start the "Virtual Heart Rate Monitor" app on the Android device.
   - You should see the BPM value update with each new received value.
3. On your exercise equipment (or app on your second mobile device), search for a Bluetooth heart rate monitor.  
   - The mock heart rate monitor will have the same name as the Android device.

### Disclaimer

No implied warranty or guarantee of functionality. The names Fitbit, Android, and Bluetooth are trademarks of their respective owners.

[Privacy Policy](privacy-android.md)
