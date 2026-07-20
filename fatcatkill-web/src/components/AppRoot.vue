<script setup>
import { ref, watch } from 'vue'
import FeatureMenu from './FeatureMenu.vue'
import LoginPanel from './LoginPanel.vue'
import GameHeader from './GameHeader.vue'
import GameOverPanel from './GameOverPanel.vue'
import RoomSetupPanel from './RoomSetupPanel.vue'
import HostObserverPanel from './HostObserverPanel.vue'
import PlayerGamePanel from './PlayerGamePanel.vue'
import '../styles/App.css'
import { useFatcatApp } from '../composables/useFatcatApp'
import { translateMessage, useI18n } from '../i18n'

const {
  authMode,
  authUsername,
  authPassword,
  nickname,
  userId,
  roomId,
  roomSize,
  authUser,
  displayName,
  showRoomList,
  connectedRooms,
  historyRecords,
  historyLoading,
  historyWinnerText,
  historyTimeText,
  submitAuth,
  loginAsGuest,
  logoutAuth,
  createRoom,
  createHostedRoom,
  openRoomList,
  requestRoomList,
  selectRoom,
  loadGameHistory,
  downloadHistoryRecord,
  isConnected,
  isNight,
  gameState,
  currentPhaseText,
  topbarUserLabel,
  isRoomHost,
  leaveRoom,
  actionError,
  actionNotice,
  pendingKickPlayer,
  winnerText,
  downloadGameLogs,
  returnToMain,
  selectedMode,
  selectedSavedDeck,
  isDebugMode,
  revealPlayers,
  testRoleAssignments,
  testRoleOptions,
  customDeck,
  hostAdvancedOpen,
  highRabbitRole,
  hostMethaneHallucinationTargetId,
  deckName,
  serverMessage,
  roomOccupancyText,
  isRoomParticipant,
  isPlayerReady,
  roomFillPercent,
  roomPlayerCount,
  roomOpenSlots,
  roomCapacity,
  roomSeatSlots,
  seatTitle,
  seatActionText,
  setupRoomPlayers,
  roomHostId,
  modeLabels,
  isHostSpectator,
  savedDecks,
  customRoleOptions,
  roleName,
  hostAdvancedSummary,
  fatcatHintRoles,
  fatcatHintSlotIndexes,
  fatcatHintOptionsFor,
  hostDeckHasHighRabbit,
  highRabbitRoleOptions,
  hostDeckHasMethane,
  hostMethaneTargetOptions,
  deckValidation,
  roomStartStatus,
  canStartGame,
  toggleReady,
  fetchRoomState,
  handleSeatClick,
  confirmKickRoomPlayer,
  cancelKickRoomPlayer,
  loadHostDeck,
  setFatcatHintRole,
  saveHostDeck,
  createMockRoom,
  fetchReveal,
  startGame,
  fillBotsOnly,
  isObserverMode,
  observerTitle,
  observerSubtitle,
  botActionButtonText,
  phase,
  hostVoteRows,
  hostConfirmedVotes,
  hostVoteTotals,
  hostActivityLogs,
  hostActionLabel,
  hostPlayerLabel,
  hostLogTime,
  hostLogDetail,
  autoPlayBot,
  phServiceTargetRole,
  myDisplayedRole,
  myPlayer,
  myRoleHint,
  myEffectiveRoleHint,
  myPrivateMessage,
  ratManCheckerLabels,
  dayFlowText,
  confirmedAliveCount,
  eligibleVoters,
  canUseDayVote,
  lastVoteRows,
  methaneSelection,
  mubaimuSelection,
  shushuSelection,
  canUseFatcatKill,
  canUseChenAction,
  canUseSaltedFishAction,
  canUseFatcatTeamHint,
  fatcatHintButtonText,
  volunteerRoleOptions,
  tallyButtonText,
  canActAsRole,
  voteCountFor,
  isMyVotedPlayer,
  canVoteFor,
  dayVoteButtonText,
  confirmVote,
  cancelVote,
  skipVote,
  strAction,
  fatcatKill,
  liverAction,
  canManAction,
  nangongAction,
  handleMubaimuClick,
  handleShushuClick,
  handleMethaneClick,
  mochiBossCheck,
  chenAction,
  fishAction,
  dayVote,
  fatcatTeamHint,
  sendPhServiceAction,
  emperorReveal,
  strSkip,
  guoguoAction,
  forvkusaAction,
  hatongAction,
  xiaoxiangAction,
  sendMubaimuAction,
  sendShushuAction,
  grassBeanAction,
  xiangxiangAction,
  acCatAction,
  andyAction,
  chenSkip,
  fishSkip,
  startNomination,
  tallyVotes
} = useFatcatApp()
const { t } = useI18n()
const compactUi = ref(globalThis.localStorage?.getItem('fatcatkill.compactUi') === 'true')
watch(compactUi, (value) => globalThis.localStorage?.setItem('fatcatkill.compactUi', value ? 'true' : 'false'))
</script>

<template>
  <main :class="['app-shell', { 'night-mode': isNight, 'day-mode': !isNight && gameState, 'compact-ui': compactUi }]">
    <FeatureMenu
      v-model:compact-ui="compactUi"
      :room-id="roomId"
      :has-game="Boolean(gameState)"
      @download-logs="downloadGameLogs"
    />
    <LoginPanel
      v-if="!isConnected"
      v-model:auth-mode="authMode"
      v-model:auth-username="authUsername"
      v-model:auth-password="authPassword"
      v-model:nickname="nickname"
      v-model:user-id="userId"
      v-model:room-id="roomId"
      v-model:room-size="roomSize"
      :auth-user="authUser"
      :display-name="displayName"
      :show-room-list="showRoomList"
      :connected-rooms="connectedRooms"
      :history-records="historyRecords"
      :history-loading="historyLoading"
      :history-winner-text="historyWinnerText"
      :history-time-text="historyTimeText"
      @submit-auth="submitAuth"
      @guest-login="loginAsGuest"
      @logout-auth="logoutAuth"
      @create-room="createRoom"
      @create-hosted-room="createHostedRoom"
      @open-room-list="openRoomList"
      @request-room-list="requestRoomList"
      @select-room="selectRoom"
      @load-history="loadGameHistory"
      @download-history="downloadHistoryRecord"
    />

    <section v-else class="game-board">
      <GameHeader
        :room-id="roomId"
        :title="currentPhaseText"
        :user-label="topbarUserLabel"
        :display-name="displayName"
        :is-host="isRoomHost"
        :round="gameState?.currentRound"
        :has-game="Boolean(gameState)"
        @leave-room="leaveRoom"
      />

      <div v-if="actionError" class="error-banner">{{ actionError }}</div>
      <div v-if="actionNotice" class="notice-banner">{{ actionNotice }}</div>
      <div v-if="pendingKickPlayer" class="confirm-banner">
        <span>{{ t('setup.kickConfirm', { name: pendingKickPlayer.username || t('topbar.player', { id: pendingKickPlayer.userId }), roomId }) }}</span>
        <div class="confirm-actions">
          <button class="secondary-button small" @click="cancelKickRoomPlayer">{{ t('common.cancel') }}</button>
          <button class="action-button danger small" @click="confirmKickRoomPlayer">{{ t('common.confirm') }}</button>
        </div>
      </div>
      <div v-if="gameState?.publicMessage" class="public-banner">{{ translateMessage(gameState.publicMessage) }}</div>

      <GameOverPanel
        v-if="gameState?.status === 'FINISHED' || phase === 'GAME_OVER'"
        :winner-text="winnerText"
        @download-logs="downloadGameLogs"
        @back-main="returnToMain"
      />

      <RoomSetupPanel
        v-if="!gameState || gameState.status === 'WAITING'"
        v-model:selected-mode="selectedMode"
        v-model:selected-saved-deck="selectedSavedDeck"
        v-model:custom-deck="customDeck"
        v-model:host-advanced-open="hostAdvancedOpen"
        v-model:high-rabbit-role="highRabbitRole"
        v-model:host-methane-hallucination-target-id="hostMethaneHallucinationTargetId"
        v-model:deck-name="deckName"
        v-model:test-role-assignments="testRoleAssignments"
        :server-message="serverMessage"
        :room-occupancy-text="roomOccupancyText"
        :is-room-participant="isRoomParticipant"
        :is-player-ready="isPlayerReady"
        :room-fill-percent="roomFillPercent"
        :room-player-count="roomPlayerCount"
        :room-open-slots="roomOpenSlots"
        :room-capacity="roomCapacity"
        :room-seat-slots="roomSeatSlots"
        :user-id="userId"
        :seat-title="seatTitle"
        :seat-action-text="seatActionText"
        :setup-room-players="setupRoomPlayers"
        :room-host-id="roomHostId"
        :is-room-host="isRoomHost"
        :mode-labels="modeLabels"
        :is-host-spectator="isHostSpectator"
        :saved-decks="savedDecks"
        :custom-role-options="customRoleOptions"
        :role-name="roleName"
        :host-advanced-summary="hostAdvancedSummary"
        :fatcat-hint-roles="fatcatHintRoles"
        :fatcat-hint-slot-indexes="fatcatHintSlotIndexes"
        :fatcat-hint-options-for="fatcatHintOptionsFor"
        :host-deck-has-high-rabbit="hostDeckHasHighRabbit"
        :high-rabbit-role-options="highRabbitRoleOptions"
        :host-deck-has-methane="hostDeckHasMethane"
        :host-methane-target-options="hostMethaneTargetOptions"
        :deck-validation="deckValidation"
        :room-start-status="roomStartStatus"
        :can-start-game="canStartGame"
        :test-role-options="testRoleOptions"
        @toggle-ready="toggleReady"
        @refresh-room="fetchRoomState"
        @seat-click="handleSeatClick"
        @load-host-deck="loadHostDeck"
        @set-fatcat-hint-role="setFatcatHintRole"
        @save-host-deck="saveHostDeck"
        @create-mock-room="createMockRoom"
        @start-game="startGame"
        @fill-bots="fillBotsOnly"
      />

      <HostObserverPanel
        v-if="gameState && isObserverMode && gameState.status !== 'WAITING'"
        :title="observerTitle"
        :subtitle="observerSubtitle"
        :show-bot-button="isHostSpectator && gameState.status === 'PLAYING'"
        :bot-action-button-text="botActionButtonText"
        :players="gameState.players"
        :role-name="roleName"
        :phase="phase"
        :vote-rows="hostVoteRows"
        :confirmed-votes="hostConfirmedVotes"
        :vote-totals="hostVoteTotals"
        :night-actions="gameState.nightActions || {}"
        :action-logs="hostActivityLogs"
        :action-label="hostActionLabel"
        :player-label="hostPlayerLabel"
        :log-time="hostLogTime"
        :log-detail="hostLogDetail"
        @auto-play-bot="autoPlayBot"
      />

      <section v-if="gameState && isDebugMode" class="debug-panel">
        <div class="debug-panel-header"><span class="eyebrow">{{ t('debug.title') }}</span><button class="secondary-button" @click="fetchReveal">{{ t('debug.revealRoles') }}</button></div>
        <ul v-if="revealPlayers.length">
          <li v-for="player in revealPlayers" :key="player.userId">{{ player.userId }} - {{ player.username }}: {{ roleName(player.role) }}</li>
        </ul>
      </section>

      <PlayerGamePanel
        v-if="gameState && !isObserverMode"
        v-model:ph-service-target-role="phServiceTargetRole"
        :game-state="gameState"
        :phase="phase"
        :my-displayed-role="myDisplayedRole"
        :my-player="myPlayer"
        :my-role-hint="myRoleHint"
        :my-effective-role-hint="myEffectiveRoleHint"
        :my-private-message="myPrivateMessage"
        :rat-man-checker-labels="ratManCheckerLabels"
        :day-flow-text="dayFlowText"
        :confirmed-alive-count="confirmedAliveCount"
        :eligible-voters="eligibleVoters"
        :can-use-day-vote="canUseDayVote"
        :last-vote-rows="lastVoteRows"
        :methane-selection="methaneSelection"
        :mubaimu-selection="mubaimuSelection"
        :shushu-selection="shushuSelection"
        :can-use-fatcat-kill="canUseFatcatKill"
        :can-use-chen-action="canUseChenAction"
        :can-use-salted-fish-action="canUseSaltedFishAction"
        :is-room-host="isRoomHost"
        :bot-action-button-text="botActionButtonText"
        :can-use-fatcat-team-hint="canUseFatcatTeamHint"
        :fatcat-hint-button-text="fatcatHintButtonText"
        :volunteer-role-options="volunteerRoleOptions"
        :is-night="isNight"
        :tally-button-text="tallyButtonText"
        :role-name="roleName"
        :can-act-as-role="canActAsRole"
        :vote-count-for="voteCountFor"
        :is-my-voted-player="isMyVotedPlayer"
        :can-vote-for="canVoteFor"
        :day-vote-button-text="dayVoteButtonText"
        @confirm-vote="confirmVote"
        @cancel-vote="cancelVote"
        @skip-vote="skipVote"
        @str-action="strAction"
        @fatcat-kill="fatcatKill"
        @liver-action="liverAction"
        @can-man-action="canManAction"
        @nangong-action="nangongAction"
        @mubaimu-click="handleMubaimuClick"
        @shushu-click="handleShushuClick"
        @methane-click="handleMethaneClick"
        @mochi-boss-check="mochiBossCheck"
        @chen-action="chenAction"
        @fish-action="fishAction"
        @day-vote="dayVote"
        @auto-play-bot="autoPlayBot"
        @fatcat-team-hint="fatcatTeamHint"
        @send-ph-service-action="sendPhServiceAction"
        @emperor-reveal="emperorReveal"
        @str-skip="strSkip"
        @guoguo-action="guoguoAction"
        @forvkusa-action="forvkusaAction"
        @hatong-action="hatongAction"
        @xiaoxiang-action="xiaoxiangAction"
        @send-mubaimu-action="sendMubaimuAction"
        @send-shushu-action="sendShushuAction"
        @grass-bean-action="grassBeanAction"
        @xiangxiang-action="xiangxiangAction"
        @ac-cat-action="acCatAction"
        @andy-action="andyAction"
        @chen-skip="chenSkip"
        @fish-skip="fishSkip"
        @start-nomination="startNomination"
        @tally-votes="tallyVotes"
      />

    </section>
  </main>
</template>
