package com.fatcatkill.enums;

public enum Role {
    
    // ==========================================
    // 🐺 經典狼人殺基礎身分 (CLASSIC_6, CLASSIC_10)
    // ==========================================
    WEREWOLF,       // 狼人
    VILLAGER,       // 平民
    SEER,           // 預言家
    WITCH,          // 女巫
    HUNTER,         // 獵人
    GUARD,          // 守衛

    // ==========================================
    // ⚔️ 肥貓殺 2.0：反肥貓義勇軍本部 (好人神職)
    // ==========================================
    METHANE,        // 1. 甲烷 (永遠遷怒一個好人的預言家)
    GUOGUO,         // 2. 果果 (群公告擔當)
    XIANGXIANG,     // 3. 翔翔 (群友視姦器)
    AC_CAT,         // 4. AC貓 (後台管理員)
    FORVKUSA,       // 5. Forvkusa (死靈法師)
    HATONG,         // 6. 哈同 (查驗肥貓投票)
    KB,             // 7. KB (真群主，反殺提名者)
    SALTED_FISH,    // 8. 鹹魚 (攻擊肥貓左腳)
    XIAOXIANG,      // 9. 小翔 (社畜，查驗同盟人數)
    MOCHI_BOSS,     // 10. 麻糬幫老大 (無敵的老大，不會被暗殺)
    GRASS_BEAN,     // 11. 草盛豆苗稀 (查驗分靈體)
    NANGONG,        // 12. 南宮玉門 (買水雙封印)
    CASTER,         // 13. Caster (雙面間諜)
    ANDY,           // 14. 安弟 (暈暈，綁定雲)
    CAN_MAN,        // 15. 罐頭 (喝酒保平安)
    SINGLE_DOG,     // 16. 單身狗 (岩壁詛咒，延遲死亡)
    STR,            // 17. Str (交換座號)

    // ==========================================
    // 🎭 肥貓殺 2.0：反肥貓義勇軍同盟 (混亂/內鬼/特殊)
    // ==========================================
    HIGH_RABBIT,    // 1. 高能兔 (永遠喝醉，能力失效)
    MEATBUN,        // 2. 肉包 (永遠被視為肥貓陣營)
    MUBAIMU,        // 3. 木百木 (蛋塔超人，毒殺肥貓)
    CHEN,           // 4. 臣本布衣 (左右互踢)
    XIAOEN,         // 5. 小恩 (小丑之王，嘲諷吸招)
    NUKO,           // 6. Nuko (瘋狗，被票死則好人輸)
    SHUSHU,         // 7. 書書 (旅遊雙死)
    BARK_KING,     // 8. 定涼 (放鳥狗叫帝)

    // ==========================================
    // 🐱 肥貓殺 2.0：肥貓與肥貓分靈體 (壞人陣營)
    // ==========================================
    FATCAT,         // ◇ 肥貓 (本體，暗殺大魔王)
    LIVER_INDEX,     // 1. 肝指數大俠 (附加爆肝失靈 debuff)
    PINK_RABBIT,    // 2. 粉紅毛毛兔兔貓 (替死鬼護盾)
    EMPEROR,        // 3. 土太上皇 (開局全知)
    NTHU_MATH,  // 4. 最頂的清大數學 (增加同盟人數)
    MAGIC_MEOW,     // 5. 神奇肥貓喵喵叫 (肥貓繼承人)
    PH_SERVICE,     // 6. 屁話服務 (盜號者，偽裝好人)
    RAT_MAN;        // 7. 狡猾老鼠人 (反向查驗預言家)

    public String getDisplayName() {
        return switch (this) {
            case WEREWOLF -> "狼人";
            case VILLAGER -> "村民";
            case SEER -> "預言家";
            case WITCH -> "女巫";
            case HUNTER -> "獵人";
            case GUARD -> "守衛";
            case METHANE -> "甲烷";
            case GUOGUO -> "果果";
            case XIANGXIANG -> "翔翔";
            case AC_CAT -> "AC 貓";
            case FORVKUSA -> "Forvkusa";
            case HATONG -> "哈同";
            case KB -> "KB";
            case SALTED_FISH -> "鹹魚";
            case XIAOXIANG -> "小翔";
            case MOCHI_BOSS -> "麻糬幫老大";
            case GRASS_BEAN -> "草盛豆苗稀";
            case NANGONG -> "南宮玉門";
            case CASTER -> "Caster";
            case ANDY -> "安弟";
            case CAN_MAN -> "罐頭";
            case SINGLE_DOG -> "單身狗";
            case STR -> "Str";
            case HIGH_RABBIT -> "高能兔";
            case MEATBUN -> "肉包";
            case MUBAIMU -> "木百木";
            case CHEN -> "臣本布衣";
            case XIAOEN -> "小恩";
            case NUKO -> "Nuko";
            case SHUSHU -> "書書";
            case BARK_KING -> "定涼";
            case FATCAT -> "肥貓";
            case LIVER_INDEX -> "肝指數大俠";
            case PINK_RABBIT -> "粉紅毛毛兔兔貓";
            case EMPEROR -> "土太上皇";
            case NTHU_MATH -> "最頂的清大數學";
            case MAGIC_MEOW -> "神奇肥貓喵喵叫";
            case PH_SERVICE -> "屁話服務";
            case RAT_MAN -> "狡猾老鼠人";
        };
    }
}