{
  description = "MountX Android app development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      androidSdk = pkgs.androidenv.composeAndroidPackages {
        cmdLineToolsVersion = "latest";
        platformToolsVersion = "37.0.1";
        buildToolsVersions = [ "35.0.0" ];
        platformVersions = [ "35" ];
        includeEmulator = false;
        includeCmake = false;
        includeNDK = false;
        includeSources = false;
        includeSystemImages = false;
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          temurin-bin-17
          androidSdk.androidsdk
          bash
          coreutils
          findutils
          git
          gawk
          gnused
          unzip
          zip
        ];

        ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
        JAVA_HOME = "${pkgs.temurin-bin-17}";

        shellHook = ''
          echo "MountX development shell (JDK 17, Android SDK 35)"
          echo "Build debug APK:   bash gradlew assembleDebug"
          echo "Build release APK: bash gradlew assembleRelease"
          echo "Run unit tests:    bash gradlew testDebugUnitTest"
        '';
      };
    };
}
