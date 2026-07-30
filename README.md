# FlySafe Weather

Android weather app for drone pilots and aviators: METAR decoding, NWS forecasts,
TFR and controlled airspace awareness, GNSS satellite status, Kp index, and sunrise/sunset planning.

## Download

Download page: https://doorcountydrone.github.io/FlySafeWeatherApp/

Direct APK: https://github.com/doorcountydrone/FlySafeWeatherApp/releases/latest/download/FlySafeWeather.apk

After downloading, open the file and allow install from this source if asked.

## Building from source

API keys are not committed. To build:

1. Copy `secrets.properties.example` to `secrets.properties`.
2. Fill in your own Google Maps and FAA API credentials.
3. Open the project in Android Studio and run the `app` module.

`local.properties`, `secrets.properties`, and `keystore.properties` are git-ignored.

## Data sources

Aviation weather (METAR), NWS forecasts, FAA TFR/NOTAM data, and space weather indices.
For situational awareness only — not for navigation.
