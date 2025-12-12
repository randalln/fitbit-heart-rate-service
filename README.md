# FB Heart Rate Monitor (for Fitbit Sense, Versa 3, Versa 2, Versa Lite, Versa, and Ionic)

A pair of apps: one for a Fitbit&trade; smart watch, and one for an Android or iOS device, that 
create a mock Bluetooth heart rate monitor.

This lets you share real time heart rate data from the Fitbit watch with exercise equipment (like 
Peloton) or fitness apps (like Strava, Zwift, or Wahoo).

Currently, the Fitbit&trade; smart watches measure heart rate, but do not advertise as generic 
heart rate monitors and can only maintain one Bluetooth Low Energy connection with the 
Fitbit&trade; mobile app.
As a workaround, we'll send heart rate data from the watch to the mobile device. Then, we'll run 
our app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart 
rate monitor. Finally, we'll push heart rate data from the Fitbit mobile app to our **FB Heart Rate 
Monitor** app.

Much thanks to the original author, [@mogenson](https://github.com/mogenson/fitbit-heart-rate-service)

Thanks to [@cwcpers](https://github.com/cwcpers/versa2-heart-rate-service?tab=readme-ov-file), 
who got the watch app working on OS 4 devices (Versa 2, Versa Lite, Versa, Ionic)

## Requirements

1. One of the Fitbit&trade; smart watches (with the Fitbit Gallery) noted above and a device 
   running at least Android 9 or iOS 15.6
2. If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second 
   device, because **FB Heart Rate Monitor** will need to run on the same (bluetooth) device as the 
   Fitbit&trade; app.  
   For example, I run Wahoo or Strava on my iPad or spare old Android phone and **FB Heart Rate 
   Monitor** on my regular phone (where Fitbit&trade; is installed)   

## Installation

1. Install the **HR Service** Fitbit watch app from the Fitbit Gallery
    * [Sense, Versa 3](https://gallery.fitbit.com/details/6503a799-37f7-43bf-8775-23f1742e2a4e) 
    * [Versa 2, Versa Lite, Versa, Ionic](https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124?key=c05ef0f4-c8f7-4459-94f7-50a209108fc6)
2. Install the **FB Heart Rate Monitor** app:
#### Android
[<img alt="Install from Google Play" src="GetItOnGooglePlay_Badge_Web_color_English.png">](https://play.google.com/store/apps/details?id=org.noblecow.hrservice)
#### iOS
1. Install [AltStore Classic](https://faq.altstore.io/)
2. Add a new source to AltStore Classic: https://raw.githubusercontent.com/randalln/fitbit-heart-rate-service/main/companion/AltStore/ClassicSource.json
3. Install **FB Heart Rate Monitor**


## Usage
1. Start the **HR Service** app on the Fitbit watch.
2. Start the **FB Heart Rate Monitor** app on the mobile device.
    - You should see the BPM value update with each new received value.
3. On your exercise equipment or an app like Strava (on your second mobile device), search for a 
   Bluetooth heart rate monitor.
    - The mock heart rate monitor will have the same name as the mobile device.

    
### Disclaimer

No implied warranty or guarantee of functionality. The names Fitbit, Android, iOS, and Bluetooth 
are trademarks of their respective owners. 

[Privacy Policy](privacy-android.md)
