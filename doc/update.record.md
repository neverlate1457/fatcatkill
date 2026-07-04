# 2026/06/19
- commit
- fix internet issue

# 2026/07/04
- 拆分前端 App.vue 與 useFatcatApp，整理 composables、components、utils、data、config 結構。
- 整合正式版與 debug mode，改由環境變數控制是否顯示測試功能。
- 新增前端 i18n 語言切換，整理角色名稱、UI 文案與後端 message key 顯示。
- 新增簡易登入、訪客登入與歷史遊戲查詢流程。
- 調整 gateway 結構，拆出 route guard、CORS、proxy routes 與 game state sanitizer。
- 修正房間準備、房主開始遊戲、bot ready、玩家死亡觀戰模式等流程問題。
- 修正前端啟動空白畫面：補上 observer mode 狀態與觀戰模式翻譯鍵。


