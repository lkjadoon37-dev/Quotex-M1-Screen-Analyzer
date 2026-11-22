QUOTEX M1 SCREEN ANALYZER - STARTER PROJECT

Yeh Android Studio ka starter source project hai, ready APK nahi.

Is build mein:
- Android MediaProjection permission se screen capture start hota hai.
- Frames phone ki RAM mein read hotay hain.
- Screenshot file mein save nahi hota.
- Koi INTERNET permission nahi hai, is liye app khud screenshot upload nahi kar sakti.
- Foreground notification capture active honay par nazar aati hai.

Abhi is starter mein CALL/PUT/WAIT ya EMA/RSI/MACD image recognition maujood NAHI.
Yeh agla development stage hai. Chart ki image se candles aur indicators nikalna
alag computer-vision ka kaam hai; sirf screen capture se signals automatically sahi
nahi ho jatay.

Build karne ke liye:
1. Computer par Android Studio install karein.
2. Is ZIP ko extract karein.
3. Android Studio mein extracted folder open karein.
4. Gradle sync complete hone dein.
5. Android 16 phone ko USB se connect karke Run karein.
6. Screen capture permission sirf apni marzi se allow karein.

Agar aapke paas computer nahi hai to is project ko APK mein compile karne ke liye
Android build service ya kisi trusted developer ki zaroorat hogi. APK abhi is ZIP
mein shamil nahi hai.

Privacy:
- App manifest mein INTERNET permission nahi di gayi.
- Screen capture Android ka system permission dialog dikhata hai.
- Capture ko notification/Android system controls se band kiya ja sakta hai.

Trading warning:
Yeh trading advice ya guaranteed signal system nahi. Pehle demo account par
test karein. Binary options mein poora stake khona mumkin hai.


CLOUD BUILD WORKFLOW:
Is project mein .github/workflows/build-apk.yml add hai.
GitHub repository ke main branch par upload/push hone ke baad cloud runner
debug APK build karke workflow artifact ke taur par upload karega.
Yeh workflow APK ko khud is chat mein nahi bhejta; GitHub Actions artifact
se download karna hota hai. GitHub account/connection aur repository
authorization darkaar hogi.

DEVELOPMENT UPDATE - ANALYSIS ENGINE STAGE:
- AnalysisEngine.kt add ki gayi hai. Yeh closed M1 OHLC candles par EMA 9/21,
  RSI 14, MACD 12/26/9 aur recent 20-candle support/resistance calculate karti hai.
- Output CALL / PUT / WAIT aur setup-strength score deta hai. Score win probability nahi.
- Engine abhi screen capture se connect NAHI hai. Current capture service frames read karke
  discard karti hai. Agla kaam chart area/candle pixels ko locally detect karke OHLC data
  banana aur sirf candle close par engine ko call karna hai.
- Jab tak screen-to-candle recognition implement aur test na ho, isay live trading signal
  ke liye istemal na karein. Pehle demo/paper testing zaroori hai.
