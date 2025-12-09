# FB Heart Rate Monitor (for Fitbit Sense and Versa 3)

A pair of apps: one for a Fitbit smart watch, and one for an Android or iOS device, that create a 
mock Bluetooth heart rate monitor.

This lets you share real time heart rate data from the Fitbit watch with exercise equipment like 
Peloton, Strava, Zwift, or Wahoo.

[@cwcpers](https://github.com/cwcpers/versa2-heart-rate-service?tab=readme-ov-file) has gotten the 
[watch app](https://gallery.fitbit.com/search?terms=HR%20Service)
working on [OS 4 devices](https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124?key=c05ef0f4-c8f7-4459-94f7-50a209108fc6) too! (Versa 2, Versa Lite, Versa, Ionic)

![Apps](./apps.jpg)

Currently, the Fitbit smart watches measure heart rate, but do not advertise as generic heart 
rate monitors and can only maintain one Bluetooth Low Energy connection with the Fibit phone app.
As a work around, we'll send heart rate data from the watch to the phone. Then, we'll run an 
Android app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart 
rate monitor. Finally, we'll push heart rate data from the Fitbit Android app to our HR Monitor 
Android app.

Much thanks to the original author, [@mogenson](https://github.com/mogenson/fitbit-heart-rate-service)

## Requirements

1. A Fitbit Sense or Versa 3 smart watch (or an OS 4 device noted above) and a device running at 
   least Android 9.0 (Pie) or iOS 15.6.
2. If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second 
   device, because FB Heart Rate Monitor will need to run on the same (bluetooth) device as the 
   Fitbit&trade; app.  
   For example, I run Wahoo or Strava on my iPad or spare old Android phone and FB Heart Rate 
   Monitor on my regular phone (where Fitbit&trade; is installed)   

## Installation

1. Install the "HR Service" Fitbit watch app from the [Fitbit Gallery](https://gallery.fitbit.com/details/6503a799-37f7-43bf-8775-23f1742e2a4e)
2. Install the "FB Heart Rate Monitor" app:
#### Android
[<img alt="Install from Google Play" src="GetItOnGooglePlay_Badge_Web_color_English.png">](https://play.google.com/store/apps/details?id=org.noblecow.hrservice)
#### iOS
1. Install [AltStore Classic](https://faq.altstore.io/)
2. Add a new source to AltStore Classic: https://raw.githubusercontent.com/randalln/fitbit-heart-rate-service/main/companion/AltStore/ClassicSource.json
3. Install FB Heart Rate Monitor.


## Usage
1. Start the "HR Service" app on the Fitbit watch.
2. Start the "FB Heart Rate Monitor" app on the mobile device.
    - You should see the BPM value update with each new received value.
3. On your exercise equipment (or app on your second mobile device), search for a Bluetooth heart rate monitor.
    - The mock heart rate monitor will have the same name as the mobile device.

    
### Disclaimer

No implied warranty or guarantee of functionality. The names Fitbit, Android, and Bluetooth are 
trademarks of their respective owners. 

[Privacy Policy](privacy-android.md)
