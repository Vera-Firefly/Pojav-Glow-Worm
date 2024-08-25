# !/bin/user

echo "Start Get Jre"
git clone https://github.com/Vera-Firefly/android-openjdk-autobuild

if [ -z "$GET_JRE" ]; then
  echo "Nvironment GET_JRE is not set, default to get jre8 and jre21"
  rm android-openjdk-autobuild/LatestJre/jre-11
  rm android-openjdk-autobuild/LatestJre/jre-17
  cp -rf android-openjdk-autobuild/LatestJre/* app_pojavlauncher/src/main/assets/components/
else
  if [ "$GET_JRE" -eq 1 ]; then
    cp -rf android-openjdk-autobuild/LatestJre/* app_pojavlauncher/src/main/assets/components/
  elif [ "$GET_JRE" -eq 2 ]; then
    rm android-openjdk-autobuild/LatestJre/jre-11
    cp -rf android-openjdk-autobuild/LatestJre/* app_pojav_zh/src/main/assets/components/
  fi
fi

echo "Setup Complete"


