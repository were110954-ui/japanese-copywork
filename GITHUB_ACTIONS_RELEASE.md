# GitHub Actions APK 자동 배포 설정

## 1. Release 서명 키 준비

한 번 정한 키는 잃어버리거나 교체하면 기존 앱을 업데이트할 수 없습니다. 안전한 곳에
별도 백업합니다.

```powershell
keytool -genkeypair -v -keystore copywork-release.jks -alias copywork -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("copywork-release.jks")) | Set-Clipboard
```

GitHub 저장소의 **Settings → Secrets and variables → Actions → New repository secret**에서
다음을 등록합니다.

- `ANDROID_KEYSTORE_BASE64`: 위 명령으로 복사한 Base64 전체
- `ANDROID_KEYSTORE_PASSWORD`: keystore 비밀번호
- `ANDROID_KEY_ALIAS`: 예시에서는 `copywork`
- `ANDROID_KEY_PASSWORD`: key 비밀번호

## 2. Actions 쓰기 권한

**Settings → Actions → General → Workflow permissions**에서 **Read and write permissions**를
선택하고 저장합니다. 워크플로우가 Release와 태그를 만들고 `version.json`을 main에
커밋하려면 필요합니다.

main 브랜치 보호 규칙이 봇의 직접 push를 막는 경우 `github-actions[bot]` 우회 허용을
설정하거나 `version.json` 갱신을 PR 방식으로 변경해야 합니다.

## 3. 웹에서 버튼 한 번으로 배포

1. 저장소의 **Actions** 탭을 연다.
2. **Build and release Android APK**를 선택한다.
3. **Run workflow**를 누른다.
4. `version_name`에 `1.2.1`처럼 `v` 없이 입력한다.
5. `version_code`는 이전 값보다 큰 정수를 입력한다. 비우면 `1000 + Actions run number`가 사용된다.
6. 변경점을 입력하고 실행한다.

완료되면 `v1.2.1` Release와 `app-release.apk`가 생성되고 main의 `version.json`도 자동
갱신된다. 앱은 다음 실행 시 이 JSON을 읽고 새 APK를 안내한다.

## 4. 태그로 배포

```bash
git tag v1.2.1
git push origin v1.2.1
```

태그 이름에서 versionName을 만들며 versionCode는 `1000 + Actions run number`를 사용한다.

> 지금까지 배포한 APK는 Android Debug 키로 서명되었다. 새 CI release 키 APK는 기존
> debug 설치본 위에 설치할 수 없으므로 최초 한 번 기존 앱을 제거하고 release APK를
> 설치해야 한다. 이후 CI 빌드는 같은 Secrets 키를 사용하므로 인앱 업데이트가 유지된다.
