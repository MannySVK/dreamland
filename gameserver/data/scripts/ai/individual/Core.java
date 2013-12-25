/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ai.individual;

import ai.AbstractNpcAI;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.util.Rnd;

/**
 * Core AI
 * @author DrLecter Revised By Emperorc
 */
public class Core extends AbstractNpcAI
{
	// Monsters
	private static final int CORE = 45050;
	private static final int SUSCEPTOR = 45051;
	private static final int WAVE = 45052;
	private static final int DESTROYER = 45053;
	private static final int MINIONS[] = 
	{
		DESTROYER, WAVE, WAVE, SUSCEPTOR, SUSCEPTOR, SUSCEPTOR, SUSCEPTOR
	};
	
	// State
	private static final byte DEAD = 0;
	private static final byte ALIVE = 1;
	private static final byte ATTACKED = 2;
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours 
	private static final int SPAWN_DELAY = 86400000; // 1 day 
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_IDLE = 15; // 15 * maintenance
	private static final int TIMER_MINION = 60000; // 1 minute
	
	// Other
	private static List<L2Attackable> _minions = new ArrayList<>();
	private static final Location _location = new Location(17734, 108912, -6480, 0);
	private static byte _idle = 0;
	private static final double REBOOT_HP = 0.1; // 10%
	private static int _reboot = 0;
	
	public Core(String name, String descr)
	{
		super(name, descr);
		
		addEventId(CORE, QuestEventType.ON_ATTACK);
		addEventId(CORE, QuestEventType.ON_KILL);
		addEventId(DESTROYER, QuestEventType.ON_KILL);
		addEventId(WAVE, QuestEventType.ON_KILL);
		addEventId(SUSCEPTOR, QuestEventType.ON_KILL);
		
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(CORE);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("core_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("core_maintenance"))
		{
			if (_idle > 0 && --_idle == 0)
			{
				GrandBossManager.getInstance().updateBossState(CORE, ALIVE);
				despawnMinions();
				npc.broadcastNpcSay("None intruder detected, area is clear.");
				npc.broadcastNpcSay("Stopping intruder removal system.");
			}
			else
				GrandBossManager.getInstance().updateBossData(CORE, true);
		}
		else if (event.equalsIgnoreCase("core_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(CORE));
		}
		else if (event.equalsIgnoreCase("core_reboot"))
		{
			if (npc.isDead())
			{
				npc.broadcastNpcSay("Failed to reboot system...");
				_reboot = 0;
			}
			else
			{
				switch (_reboot)
				{
					case 30:
						npc.broadcastNpcSay("Rebooting in 30 seconds.");
						startQuestTimer("core_reboot", 20000, npc, null, 0);
						_reboot = 10;
						break;
					case 10:
						npc.broadcastNpcSay("Rebooting in 10 seconds.");
						startQuestTimer("core_reboot", 5000, npc, null, 0);
						_reboot = 5;
						break;
					case 5:
						npc.broadcastNpcSay("Rebooting in 5 seconds.");
						startQuestTimer("core_reboot", 2000, npc, null, 0);
						_reboot = 3;
						break;
					case 3:
						npc.broadcastNpcSay("Rebooting in 3 seconds.");
						startQuestTimer("core_reboot", 1000, npc, null, 0);
						_reboot = 2;
						break;
					case 2:
						npc.broadcastNpcSay("Rebooting in 2 seconds.");
						startQuestTimer("core_reboot", 1000, npc, null, 0);
						_reboot = 1;
						break;
					case 1:
						npc.broadcastNpcSay("Rebooting...");
						npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());
						_reboot = 0;
						npc.broadcastNpcSay("System successfully rebooted.");
						break;
				}
			}
		}
		else if (event.equalsIgnoreCase("minion_spawn"))
		{
			L2GrandBossInstance core = GrandBossManager.getInstance().getBossData(CORE).getInstance();
			core.broadcastNpcSay("Updating intruder removal system.");
			
			spawnMinion(npc.getNpcId());
		}
		else if (event.equalsIgnoreCase("minion_despawn_all"))
		{
			despawnMinions();
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		if (npc.getNpcId() == CORE)
		{
			L2GrandBossData state = GrandBossManager.getInstance().getBossData(CORE);
			if (state.getState() == ALIVE)
			{
				GrandBossManager.getInstance().updateBossState(CORE, ATTACKED);
				npc.broadcastNpcSay("A non-permitted target has been discovered.");
				npc.broadcastNpcSay("Starting intruder removal system.");
				spawnMinions();
			}
			else 
			{
				if (Rnd.get(100) < 1)
					npc.broadcastNpcSay("Removing intruders.");
				
				if (_reboot == 0 && npc.getCurrentHp() - damage < npc.getMaxHp() * REBOOT_HP)
				{
					npc.broadcastNpcSay("Intruder removal system has failed.");
					npc.broadcastNpcSay("Attempting to reboot...");
					startQuestTimer("core_reboot", 30000, npc, null, 0);
					_reboot = 30;
				}
			}
			_idle = TIMER_IDLE;
		}
		return super.onAttack(npc, attacker, damage, isPet);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		if (npc.getNpcId() == CORE)
		{
			cancelQuestTimers("core_maintenance");
			cancelQuestTimers("core_reboot");
			cancelQuestTimers("minion_spawn");
			
			int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
			final L2GrandBossData data = GrandBossManager.getInstance().getBossData(CORE);
			data.setRespawn(System.currentTimeMillis() + respawnTime);
			GrandBossManager.getInstance().updateBossState(CORE, DEAD);
			GrandBossManager.getInstance().cleanClans(CORE);
			// TODO: remove players from zone
			
			startQuestTimer("core_unlock", respawnTime, null, null, 0);
			startQuestTimer("minion_despawn_all", 20000, null, null, 0);

			npc.broadcastPacket(new PlaySound(1, "BS02_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
			npc.broadcastNpcSay("A fatal error has occurred.");
			npc.broadcastNpcSay("System is being shut down...");
		}
		else if (_minions.remove(npc))
		{
			if (GrandBossManager.getInstance().getBossData(CORE).getState() == ATTACKED)
				startQuestTimer("minion_spawn", TIMER_MINION, npc, null, 0);
		}
		return super.onKill(npc, killer, isPet);
	}
	
	// TODO: BOSS
	
	private void trySpawnBoss(L2GrandBossData data)
	{
		List<Integer> clans = data.getClans();
		if (clans.isEmpty())
		{
			long respawn = data.getRespawn();
			do
			{
				respawn += SPAWN_DELAY;
			}
			while (respawn < System.currentTimeMillis());
			
			data.setRespawn(respawn);
			GrandBossManager.getInstance().updateBossState(CORE, DEAD);
			startQuestTimer("core_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		L2GrandBossInstance raid = (L2GrandBossInstance) addSpawn(CORE, _location, false, 0, false);
		if (data.getState() == DEAD)
		{
			GrandBossManager.getInstance().updateBossState(CORE, ALIVE);
		}
		else
		{
			raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
			if (data.getState() == ATTACKED)
			{
				spawnMinions();
				_idle = TIMER_IDLE;
			}
		}
		data.setInstance(raid);
		startQuestTimer("core_maintenance", TIMER_MAINTENANCE, raid, null, TIMER_MAINTENANCE);
		raid.broadcastPacket(new PlaySound(1, "BS01_A", 1, raid.getObjectId(), raid.getX(), raid.getY(), raid.getZ()));
	}
	
	// TODO: MINIONS
	
	private void spawnMinions()
	{
		for (int npcId : MINIONS)
			spawnMinion(npcId);
	}
	
	private void spawnMinion(int npcId)
	{
		int x = Rnd.get(2) < 1 ? Rnd.get(150, 300) : Rnd.get(-300, -150);
		int y = Rnd.get(2) < 1 ? Rnd.get(150, 300) : Rnd.get(-300, -150);
		L2Attackable minion = (L2Attackable) addSpawn(npcId, x + 17734, y + 108912, -6476, 0, false, 0, true);
		minion.setIsRaidMinion(true);
		_minions.add(minion);
	}
	
	private static void despawnMinions()
	{
		for (L2Attackable minion : _minions)
		{
			if (minion != null)
				minion.deleteMe();
		}
		_minions.clear();
	}
	
	public static void main(String[] args)
	{
		new Core(Core.class.getSimpleName(), "ai/individual");
	}
}