dungeon.takeover();

onTrigger('onEnter', async (event) => {
  if (dungeon.factFlag('entries_registered')) {
    return;
  }
  dungeon.setFact('entries_registered', true);
  if (dungeon.playerCount() >= 2) {
    dungeon.registerEntry('contract_swarm', 'SLOWNESS:1200:0', '虫群合约: 全员缓慢 60 秒');
  } else {
    dungeon.registerEntry('contract_solo', 'JUMP_BOOST:1200:0', '独行合约: 全员跳跃提升 60 秒');
  }
  dungeon.hint('<yellow>流程脚本已接管本局推进</yellow>', 'title');
});

onTrigger('onGateOpen', (event) => {
  dungeon.hint('<green>门禁已开启: ' + (event.gateId || '') + '</green>', 'chat');
});

async function runFlow() {
  await waitFor('onEnter');
  dungeon.hint('<yellow>目标: 进入第一个房间</yellow>', 'chat');
  await waitFor('onRoomEnter');
  dungeon.spawnWave('sp_hall_entry', 0);
  await waitFor('onWaveClear');
  dungeon.openGate('gate_hall_exit');
  await waitFor('onRoomClear');
  dungeon.enterRoom('room_two');
  await waitFor('onRoomEnter');
  dungeon.hint('<yellow>击败 Boss 后完成结算</yellow>', 'chat');
  await waitFor('onBossKill');
  dungeon.complete('script');
}

runFlow();
