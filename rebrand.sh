#!/bin/sh
set -e
cd "$(dirname "$0")"

APP_ID="com.cq61tech.whatsposed"
VERSION_NAME="0.1.0"
VERSION_CODE="1"
REPO="cq61tech/WhatsPosed"

GRADLE=app/build.gradle.kts
JAVA=app/src/main/java/com/wmods/wppenhacer
RES=app/src/main/res

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

echo "--- remaining ---"
grep -rIn 'WaEnhancer\|Wa Enhancer\|WAE\|t\.me/' app/src .github --include=*.kt --include=*.java --include=*.xml --include=*.yml --include=*.kts --include=*.prop --exclude-dir=cpp | grep -v 'CallRecording\.kt' | grep -v 'Based on WaEnhancer' || true
echo "--- done ---"
