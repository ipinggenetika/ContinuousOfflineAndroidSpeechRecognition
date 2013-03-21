Continuous Android Speech-to-Text
=================================

This app uses CMU PocketSphinx as a keyword spotter that activates the Android Speech Recognition intent.

*NOTE:*
I have discontinued this project. Instead, I am running a service that launches intents such that the Android Speech Recognizer constantly listens in the background, and only launches its UI after a keyword has been recognized.


*NOTE:*
This app will not run without manually installing the acoustic and language models on the device's SD card.
Follow these instructions: http://swathiep.blogspot.com/2011/02/offline-speech-recognition-with.html
