# My Music Android

가사 화면은 MP3 내장 가사를 우선 사용하고, 없으면 LRCLIB에서 검색합니다. 싱크 가사가 있으면 재생 시간에 맞춰 각 줄을 강조합니다.

영어·일본어 가사는 한국어 번역, 원문, 한글 발음 순서로 표시합니다. 번역에는 [Google Translate](https://translate.google.com/)를 구동하는 ML Kit 기기 내 번역을 사용합니다. 언어 모델은 처음 사용할 때 Wi-Fi로 내려받고, 이후 저장된 번역과 모델은 오프라인에서도 사용할 수 있습니다. 자동 번역과 한글 발음은 정확하지 않을 수 있으므로 원문을 함께 표시합니다. 일본어 발음에는 Kuromoji IPADIC 읽기 사전, 영어 발음에는 CMU Pronouncing Dictionary를 사용합니다.

Google Translate 번역 결과에 대한 안내는 가사 화면의 우측 상단 메뉴에서 볼 수 있습니다. 영어 발음 사전의 저작권 고지는 앱 에셋의 `CMUDICT-LICENSE`에 포함되어 있습니다.
