# DALL·E 앱 아이콘 프롬프트

아래 프롬프트를 그대로 사용하고, 결과물은 Android Adaptive Icon용 1024×1024 PNG로 생성한다.

> Create a premium minimalist Android app icon for a Japanese editorial copywork study app. Center a single elegant Japanese calligraphy brush subtly merging into a modern mechanical pencil, drawing one short navy ink stroke that suggests the character “日” without becoming a readable logo. Warm soft-cream paper background (#F8F7F2), deep slate navy ink (#2C3E50), one restrained sage-green accent (#4E6E5D). Calm, scholarly, trustworthy Japanese stationery aesthetic, precise geometry, generous negative space, soft tactile paper depth, crisp silhouette at 48px, no words, no letters, no gradients, no photorealism, no mockup, no border, no drop shadow outside the central symbol. Center-safe composition with all essential artwork inside the middle 66 percent for Android adaptive-icon masking. Square 1024×1024.

네거티브 지시어:

> Avoid red sun clichés, flags, cherry blossoms, anime, mascots, clutter, tiny details, illegible kanji, text, watermark, glossy 3D effects, heavy shadows, and low contrast.

## Android 적용 방법

1. DALL·E 결과를 1024×1024 PNG로 저장한다.
2. Android Studio에서 `app` 우클릭 → **New → Image Asset**를 연다.
3. **Launcher Icons (Adaptive and Legacy)**를 선택하고 PNG를 Foreground Layer로 지정한다.
4. 배경색을 `#F8F7F2`, 이름을 `ic_launcher`로 두고 Finish를 누른다.
5. 생성된 `mipmap-*` 및 `mipmap-anydpi-v26` 파일을 커밋한다.
6. Manifest의 `<application>`에 다음을 지정한다.

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

Play Store 등록용 아이콘은 같은 원본을 512×512 PNG로 축소하되 투명 배경 없이 업로드한다.
