# JJY FILE ORGANIZER - 단일 EXE 만들기

이 패키지는 GitHub의 무료 Windows 빌드 환경을 이용해
JJY_FILE_ORGANIZER.exe를 만드는 용도입니다.

중요:
- 내 PC에 Python 설치 불필요
- BUILD_EXE.bat 실행 불필요
- 최종 결과는 Windows용 단일 EXE 파일

## 한 번만 하면 되는 준비

1. GitHub 계정이 없다면 github.com에서 무료 계정을 만듭니다.
2. GitHub에서 New repository를 누릅니다.
3. Repository name에 예를 들어 JJY-File-Organizer를 입력합니다.
4. Public 또는 Private를 선택합니다.
5. Create repository를 누릅니다.
6. 새 저장소 화면에서 Add file → Upload files를 누릅니다.
7. 이 ZIP을 압축 해제한 뒤 안의 파일과 폴더를 모두 업로드합니다.
   특히 .github 폴더도 반드시 함께 업로드해야 합니다.
8. Commit changes를 누릅니다.

## EXE 만들기

1. GitHub 저장소 상단의 Actions를 누릅니다.
2. 왼쪽에서 Build JJY File Organizer EXE를 선택합니다.
3. Run workflow 버튼을 누릅니다.
4. 다시 Run workflow를 누릅니다.
5. 약 1~3분 정도 기다립니다.
6. 작업이 완료되면 초록색 체크 표시가 나타납니다.
7. 해당 작업을 클릭합니다.
8. 아래쪽 Artifacts에서 JJY_FILE_ORGANIZER를 다운로드합니다.
9. 압축을 풀면 JJY_FILE_ORGANIZER.exe가 있습니다.

이 EXE는 다른 Windows PC에서도 Python 설치 없이 바로 실행할 수 있습니다.

## 프로그램 기능

- 선택한 특정 폴더와 모든 하위 폴더 검사
- MP3 → D:\개인자료\JJY DATA\MP3
- MP3 파일명에 MR 포함 → D:\개인자료\JJY DATA\MR
- 이미지 → D:\개인자료\JJY DATA\IMAGE
- PDF → D:\개인자료\JJY DATA\PDF
- PDF 파일명 끝이 b1~b6, 1b~6b, #1~#6, 1#~6#, _C → D:\개인자료\JJY DATA\악보
- 한글 문서(HWP/HWPX 등) → 한글문서
- MS Office 문서 → MS문서
- 압축 파일(ZIP/7Z/RAR 등) → 압축파일
- CAD/3D 파일(DWG/DXF/DWF/STP/STEP/IGS/STL/OBJ 등) → 캐드파일
- 기타 모든 파일 → 미분류
- 정리 전 미리보기
- 파일별 체크 해제
- 중복 파일은 (1), (2), (3) 자동 생성
- 선택한 파일만 실제 이동
- 완료 후 정리 내역 표시 및 로그 저장

주의: 파일 정리는 복사가 아니라 이동입니다.
