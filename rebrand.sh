#!/bin/sh
set -e
cd "$(dirname "$0")"

APP_ID="com.cq61tech.whatsposed"
VERSION_NAME="0.2.2"
VERSION_CODE="4"
REPO="cq61tech/WhatsPosed"

GRADLE=app/build.gradle.kts
JAVA=app/src/main/java/com/wmods/wppenhacer
RES=app/src/main/res

rm -f $RES/values-night/theme_whatsposed.xml $RES/values-notnight/theme_whatsposed.xml
for d in $RES/values-night-v28 $RES/values-notnight-v28; do
    if grep -ls 'name="ThemeOverlay.MaterialGreen"' $d/* 2>/dev/null | grep -qv 'theme_whatsposed.xml'; then
        echo "duplicate overlay in $d"
        exit 1
    fi
done

sed -i "s/applicationId = \"com\.wmods\.wppenhacer\"/applicationId = \"$APP_ID\"/" $GRADLE
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" $GRADLE
sed -i "s/versionName = \"[0-9.]* (/versionName = \"$VERSION_NAME (/" $GRADLE
sed -i 's/Wa Enhancer Business/WhatsPosed Business/; s/WaEnhancer-Business/WhatsPosed-Business/; s/else -> "WaEnhancer"/else -> "WhatsPosed"/' $GRADLE

sed -i 's/WaEnhancer/WhatsPosed/g' .github/workflows/*.yml

sed -i "s#BuildConfig\.APPLICATION_ID == \"com\.wmods\.wppenhacer\"#BuildConfig.APPLICATION_ID == \"$APP_ID\"#" $JAVA/App.kt
sed -i "s#\"com\.wmods\.wppenhacer\"#\"$APP_ID\"#; s#\"com\.wmods\.wppenhacer\.w4b\"#\"$APP_ID.w4b\"#" $JAVA/utils/RootDiagnostics.kt

find app/src/main -type f \( -name '*.kt' -o -name '*.java' \) -print0 | xargs -0 sed -i 's#Download/WaEnhancer#Download/WhatsPosed#g; s#Music/WaEnhancer#Music/WhatsPosed#g; s#File(download, "WaEnhancer")#File(download, "WhatsPosed")#g'

sed -i 's/"WAE Enhancer"/"WhatsPosed"/g' $JAVA/xposed/utils/Utils.kt

sed -i "s#repos/Dev4Mod/WaEnhancer/releases/latest#repos/$REPO/releases/latest#; s#https://t\.me/waenhancher#https://github.com/$REPO/releases/latest#; s/TELEGRAM_UPDATE_URL/RELEASES_URL/g" $JAVA/UpdateChecker.kt $JAVA/ui/fragments/HomeFragment.kt

sed -i "s#https://github\.com/Dev4Mod/WaEnhancer#https://github.com/$REPO#; /btnTelegram\.setOnClickListener/d" $JAVA/activities/AboutActivity.kt

if ! grep -A1 '@+id/btnTelegram' $RES/layout/activity_about.xml | grep -q 'visibility="gone"'; then
    sed -i 's#android:id="@+id/btnTelegram"#android:id="@+id/btnTelegram"\n                        android:visibility="gone"#' $RES/layout/activity_about.xml
fi

find $RES -type f -name '*.xml' -print0 | xargs -0 sed -i 's/Wa Enhancer/WhatsPosed/g; s/WaEnhancer/WhatsPosed/g; s/\bWAE\b/WhatsPosed/g'

if [ -d app/src/main/resources ]; then
    grep -rIl 'WaEnhancer\|Wa Enhancer' app/src/main/resources | xargs -r sed -i 's/Wa Enhancer/WhatsPosed/g; s/WaEnhancer/WhatsPosed/g'
fi

sed -i '/name="about_info"/ s#\\n\\nBased on [A-Za-z]* by Dev4Mod (GPL-3\.0)\.##' $RES/values/strings.xml
sed -i '/name="about_info"/ s#</string>#\\n\\nBased on WaEnhancer by Dev4Mod (GPL-3.0).</string>#' $RES/values/strings.xml

sed -i '/android:background="@color\/nav_background"/d' $RES/layout/activity_main.xml

if ! grep -q 'id/hero_card' $RES/layout/fragment_home.xml; then
    sed -i '/tools:context="\.ui\.activity\.MainActivity">/r patches/hero_card.xml' $RES/layout/fragment_home.xml
fi
grep -q 'id/hero_card' $RES/layout/fragment_home.xml || echo "hero card was not inserted"

if ! grep -q 'wp_nav_floating' $RES/layout/activity_main.xml; then
    sed -i 's#style="@style/BottomNavStyle"#style="@style/BottomNavStyle"\n        android:background="@drawable/wp_nav_floating"\n        android:layout_marginStart="16dp"\n        android:layout_marginEnd="16dp"\n        android:layout_marginBottom="12dp"#' $RES/layout/activity_main.xml
fi

if ! grep -A1 'id/nav_shadow' $RES/layout/activity_main.xml | grep -q 'visibility="gone"'; then
    sed -i 's#android:id="@+id/nav_shadow"#android:id="@+id/nav_shadow"\n        android:visibility="gone"#' $RES/layout/activity_main.xml
fi

sed -i '/app:logo="@mipmap\/launcher"/d; s/app:elevation="4dp"/app:elevation="0dp"/' $RES/layout/activity_main.xml

grep -rl 'wae_color_mode' app/src/main/java | xargs -r sed -i 's/getString("wae_color_mode", "preset")/getString("wae_color_mode", "monet")/g'
if [ -d $RES/xml ]; then
    grep -rl 'wae_color_mode' $RES/xml | xargs -r sed -i 's/android:defaultValue="preset"/android:defaultValue="monet"/'
fi

echo "--- remaining ---"
grep -rIn 'WaEnhancer\|Wa Enhancer\|WAE\|t\.me/' app/src .github --include=*.kt --include=*.java --include=*.xml --include=*.yml --include=*.kts --include=*.prop --exclude-dir=cpp | grep -v 'CallRecording\.kt' | grep -v 'Based on WaEnhancer' || true
echo "--- done ---"
