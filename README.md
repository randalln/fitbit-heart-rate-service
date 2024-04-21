# Virtual Heart Rate Monitor (for Versa 2 and other OS 4 devices)

Entirely based off of [@randalln's work](https://github.com/randalln/fitbit-heart-rate-service).

Confirmed working with Versa 2/Samsung Galaxy S21 Ultra, connected to Peloton equipment. Basic testing using Fitbit OS Simulator looks promising for the other OS 4 devices.
Much like the previous author, I too was excited to find [@mogenson's app](https://github.com/mogenson/fitbit-heart-rate-service) after being disappointed with lackluster device capabilities.

## Original Description

This lets you share real time heart rate data from the Fitbit watch with exercise equipment like Peloton, Strava, Zwift, or Wahoo.

Currently, the Fitbit smart watches measure heart rate, but do not advertise as generic heart rate monitors and can only maintain one Bluetooth Low Energy connection with the Fibit phone app. As a work around, we'll send heart rate data from the watch to the phone. Then, we'll run an Android app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart rate monitor. Finally, we'll push heart rate data from the Fitbit Android app to our HR Monitor Android app.

## Requirements

1. A Fitbit Versa 2, Versa Lite, Versa, or Ionic smart watch and an Android phone running Android greater or equal to 8.1.
2. Install the Fitbit watch app from the ~~[Fitbit Gallery](https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124)~~
   NEW link to OS 4 compatible app on the [Fitbit Gallery - PENDING REVIEW](https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124)
   OR  
   Build and run the code using the [Fitbit CLI](https://dev.fitbit.com/getting-started/), start the developer bridge on your watch, and transfer the app.
4. Opt into [@randalln's closed testing](https://github.com/randalln/fitbit-heart-rate-service) on Google Play  
   OR  
   Download and install the APK onto your Android device from his [releases](https://github.com/randalln/fitbit-heart-rate-service/releases) page  
5. If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second device 
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
