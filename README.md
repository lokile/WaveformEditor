
# Welcome to my Waveform Editor!

Hello! I'm excited to introduce some key technologies and methodologies used in my project.


## Product Flavors

I created two product flavors: staging and production.

-   **Staging**: Includes debug logs to facilitate development and testing.
-   **Production**: All debug logs are removed.

## Using MVVM Architecture

In this project, I have employed the MVVM architecture. All the logic is handled in the ViewModel, keeping the Activity as clean as possible. There is no logic in the Activity. Additionally, I have managed activity recreation within the ViewModel.

## Unit Testing

I have implemented Unit Tests for:

-   **The ViewModel**: All functions are thoroughly tested.
-   **WaveformView**: I aimed to cover 100% of the logic in this View. However, due to time constraints, I have only created some key unit tests for this file.


## Reducing Boilerplate Code

I have developed `AppBaseActivity` to manage ViewBinding, ensuring its subclasses remain as clean as possible. Additionally, this class can help address known issues and include some ready-to-use functions to accelerate the development process.

## Baseline Profile

I added `baseline-prof.txt` to the project, which instructs the compiler to convert all my code to machine code during app installation. This enhancement boosts app performance but may increase installation time.
For long-term maintenance and updates, the baseline profile should be created following the guidelines provided by [Android Developers](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)