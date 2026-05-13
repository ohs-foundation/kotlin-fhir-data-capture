#### Running on a real iOS device

To run in a real iOS device, you'll need the following:

* The `TEAM_ID` associated with your [Apple ID](https://support.apple.com/en-us/HT204316)
* The iOS device registered in Xcode

##### Finding your Team ID

Install KDoctor with [Homebrew](https://brew.sh/):

    ```text
    brew install kdoctor
    ```

Run `kdoctor --team-ids` to find your Team ID.
KDoctor will list all Team IDs currently configured on your system, for example:

```text
3ABC246XYZ (Max Sample)
ZABCW6SXYZ (SampleTech Inc.)
```

<details>
<summary>Alternative way to find your Team ID</summary>

If KDoctor doesn't work for you, try this alternative method:

1. In Android Studio, run the `iosApp` configuration with the selected real device. The build should fail.
2. Go to Xcode and select **Open a project or file**.
3. Navigate to the `iosApp/iosApp.xcworkspace` file of your project.
4. In the left-hand menu, select `iosApp`.
5. Navigate to **Signing & Capabilities**.
6. In the **Team** list, select your team.

If you haven't set up your team yet, use the **Add account** option and follow the steps.

</details>

To run the application, set the `TEAM_ID`:

1. Navigate to the `Configuration/LocalConfig.xcconfig` file.
2. Set your `TEAM_ID`.
3. Plug in your iOS device into the computer.
4. Re-open the project in Android Studio. It should show the registered iOS device in the `iosApp` run configuration.
5. You could also open the `catalog-iosApp` project and run it with Xcode