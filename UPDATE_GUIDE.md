# GitHub Releases 자체 업데이트 배포

## 최초 설정

`android/gradle.properties`에 raw JSON 주소를 지정합니다.

```properties
UPDATE_MANIFEST_URL=https://raw.githubusercontent.com/were110954-ui/japanese-copywork/refs/heads/main/version.json
```

이 값을 넣지 않은 빌드는 업데이트 확인을 조용히 건너뜁니다. 저장소 루트에는
`version.example.json`을 복사한 `version.json`을 둡니다.

## 새 버전 배포 순서

1. `android/app/build.gradle.kts`의 `versionCode`를 반드시 증가시키고 `versionName`을 바꿉니다.
2. 항상 이전 버전과 **같은 release keystore**로 APK를 서명합니다. 키가 다르면 Android와 앱 내부 검증 모두 업데이트를 거부합니다.
3. GitHub에서 `v1.1.0` 같은 Release를 만들고 서명된 APK를 첨부합니다.
4. PowerShell에서 `Get-FileHash .\JapaneseCopywork-release.apk -Algorithm SHA256`을 실행합니다.
5. `version.json`의 코드·이름·Release APK URL·변경점·SHA-256을 갱신해 raw URL에 게시합니다.
6. 먼저 별도 기기에서 이전 버전을 설치한 후 앱 실행 → 다운로드 → 설치를 검증합니다.

앱은 HTTPS만 허용하며 APK의 SHA-256(제공된 경우), package name 및 signing certificate를
모두 검증합니다. Android 8 이상에서는 최초 한 번 시스템 설정의 “이 출처 허용”이
필요하며, 실제 설치 승인은 Android 정책상 사용자가 시스템 설치 화면에서 눌러야 합니다.
