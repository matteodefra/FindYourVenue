# FindYourVenue
---
Find Your Venue is a simple application for Android based on localization,
user photos and local database. The main idea is that the user can select an
area from integrated Google Maps and will be able to explore all the venues
in that area. The user can save his favourite venues to local DB and can
add photos. He can also see information about that place and share it

## Working flow

At the *onCreate* in *MainActivity*, the application will request permission of
localization and will ask the user to activate GPS. The data about position
is stored only on the *SharedPreferences*, so user will not have to worry
about privacy issue. The flow of the application is explained in the following
simple schema:
