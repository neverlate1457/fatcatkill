package com.fatcatkill.service;

import com.fatcatkill.model.GameActionPayload;
import com.fatcatkill.model.MessagePayload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameActionDispatcher {
    private final NightService nightService;
    private final DayService dayService;

    public GameActionDispatcher(NightService nightService, DayService dayService) {
        this.nightService = nightService;
        this.dayService = dayService;
    }

    public String dispatch(GameActionPayload action) {
        return switch (action.actionType()) {
            case "FATCAT_KILL" -> nightService.fatcatKill(action.roomId(), action.playerId(), action.targetId());
            case "FATCAT_TEAM_HINT" -> nightService.fatcatTeamHint(action.roomId(), action.playerId());
            case "EMPEROR_REVEAL" -> nightService.emperorRevealAction(action.roomId(), action.playerId());
            case "PH_SERVICE_ACTION" -> nightService.phServiceAction(action.roomId(), action.playerId(), action.targetRole());
            case "STR_ACTION" -> {
                nightService.strAction(action.roomId(), action.playerId(), action.targetId());
                yield null;
            }
            case "STR_SKIP" -> {
                nightService.strSkipAction(action.roomId(), action.playerId());
                yield null;
            }
            case "GUOGUO_ACTION" -> nightService.guoguoAction(action.roomId(), action.playerId());
            case "FORVKUSA_ACTION" -> nightService.forvkusaAction(action.roomId(), action.playerId());
            case "HATONG_ACTION" -> nightService.hatongAction(action.roomId(), action.playerId());
            case "XIAOXIANG_ACTION" -> nightService.xiaoxiangAction(action.roomId(), action.playerId());
            case "MUBAIMU_ACTION" -> nightService.mubaimuAction(action.roomId(), action.playerId(), tartTargets(action));
            case "SHUSHU_ACTION" -> nightService.shushuAction(action.roomId(), action.playerId(), action.targetId1(), action.targetId2());
            case "GRASS_BEAN_ACTION" -> nightService.grassBeanAction(action.roomId(), action.playerId());
            case "LIVER_ACTION" -> {
                nightService.liverHeroAction(action.roomId(), action.playerId(), action.targetId());
                yield null;
            }
            case "CANMAN_ACTION" -> {
                nightService.canManAction(action.roomId(), action.playerId(), action.targetId());
                yield null;
            }
            case "NANGONG_ACTION" -> {
                nightService.nangongAction(action.roomId(), action.playerId(), action.targetId());
                yield null;
            }
            case "ANDY_ACTION" -> nightService.andyAction(action.roomId(), action.playerId());
            case "METHANE_CHECK" -> {
                if (action.targetId1() == null || action.targetId2() == null) {
                    throw new LocalizedGameException(MessagePayload.of("backend.night.methaneNeedsTwoTargets", "Methane must choose two targets."));
                }
                yield nightService.methaneAction(action.roomId(), action.playerId(), action.targetId1(), action.targetId2());
            }
            case "XIANGXIANG_ACTION" -> nightService.xiangxiangAction(action.roomId(), action.playerId());
            case "AC_CAT_ACTION" -> nightService.acCatAction(action.roomId(), action.playerId());
            case "MOCHI_BOSS_CHECK" -> nightService.mochiBossCheckAction(action.roomId(), action.playerId(), action.targetId());
            case "SALTED_FISH_STAB" -> dayService.saltedFishStab(action.roomId(), action.playerId(), action.targetId());
            case "SALTED_FISH_SKIP" -> dayService.skipSaltedFishStab(action.roomId(), action.playerId());
            case "CHEN_ACTION" -> dayService.chenAction(action.roomId(), action.playerId(), action.targetId());
            case "CHEN_SKIP" -> dayService.skipChenAction(action.roomId(), action.playerId());
            default -> throw new UnknownGameActionException(action.actionType());
        };
    }

    private List<Long> tartTargets(GameActionPayload action) {
        List<Long> tartTargets = new ArrayList<>();
        if (action.targetId1() != null) tartTargets.add(action.targetId1());
        if (action.targetId2() != null) tartTargets.add(action.targetId2());
        if (action.targetId3() != null) tartTargets.add(action.targetId3());
        return tartTargets;
    }
}
