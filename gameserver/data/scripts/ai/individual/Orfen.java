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

import java.util.List;

import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.util.Rnd;

/**
 * Orfen AI
 * @author Emperorc
 */
public class Orfen extends AbstractNpcAI
{
	// Monsters
	private static final int ORFEN = 45150;
	private static final int RAIKEL_LEOS = 45151;
	private static final int RIBA_IREN = 45152;
	
	// States
	private static final byte DEAD = 0;
	private static final byte ALIVE = 1;
	private static final byte TELEPORTED = 2;
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours
	private static final int SPAWN_DELAY = 86400000; // 1 day
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	
	// Other
	private static L2BossZone _zone;
	private static final Location[] _locs = 
	{
		new Location(43728, 17220, -4342),
		new Location(55024, 17368, -5412),
		new Location(53504, 21248, -5486),
		new Location(53248, 24576, -5262),
	};
	private static final String[] _message =
	{
		"%s. Stop kidding yourself about your own powerlessness!",
		"%s. I'll make you feel what true fear is!",
		"You're really stupid to have challenged me. %s! Get ready!",
		"%s. Do you think that's going to work?!"
	};
	
	public Orfen(String name, String descr)
	{
		super(name, descr);
		
		addEventId(ORFEN, QuestEventType.ON_SKILL_SEE);
		addEventId(RAIKEL_LEOS, QuestEventType.ON_FACTION_CALL);
		addEventId(RIBA_IREN, QuestEventType.ON_FACTION_CALL);
		addEventId(ORFEN, QuestEventType.ON_ATTACK);
		addEventId(RIBA_IREN, QuestEventType.ON_ATTACK);
		addEventId(ORFEN, QuestEventType.ON_KILL);
		addEventId(RAIKEL_LEOS, QuestEventType.ON_KILL);
		
		_zone = GrandBossManager.getInstance().getZoneByXYZ(43728, 17220, -4342);
		
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(ORFEN);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("orfen_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("orfen_maintenance"))
		{
			L2GrandBossData data = GrandBossManager.getInstance().getBossData(ORFEN);
			if (data.getState() == TELEPORTED)
			{
				if (!_zone.isInsideZone(npc))
					teleport(npc, 0);
				else if (npc.getCurrentHp() / npc.getMaxHp() > 0.95)
				{
					teleport(npc, Rnd.get(3) + 1);
					data.setState(ALIVE);
				}
			}
			GrandBossManager.getInstance().updateBossData(ORFEN, true);
		}
		else if (event.equalsIgnoreCase("orfen_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(ORFEN));
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onSkillSee(L2Npc npc, L2PcInstance caster, L2Skill skill, L2Object[] targets, boolean isPet)
	{
		if (npc.getNpcId() == ORFEN)
		{
			L2Character originalCaster = isPet ? caster.getPet() : caster;
			if (skill.getAggroPoints() > 0 && Rnd.get(5) < 1 && npc.isInsideRadius(originalCaster, 1000, false, false))
			{
				npc.broadcastNpcSay(_message[Rnd.get(4)].replace("%s", caster.getName().toString()));
				originalCaster.teleToLocation(npc.getX(), npc.getY(), npc.getZ(), 0);
				npc.setTarget(originalCaster);
				npc.doCast(SkillTable.getInstance().getInfo(4064, 1));
			}
		}
		return super.onSkillSee(npc, caster, skill, targets, isPet);
	}
	
	@Override
	public String onFactionCall(L2Npc npc, L2Npc caller, L2PcInstance attacker, boolean isPet)
	{
		if (caller == null || npc == null || npc.isCastingNow())
			return super.onFactionCall(npc, caller, attacker, isPet);
		
		int npcId = npc.getNpcId();
		int callerId = caller.getNpcId();
		if (npcId == RAIKEL_LEOS && Rnd.get(20) < 1)
		{
			npc.setTarget(attacker);
			npc.doCast(SkillTable.getInstance().getInfo(4067, 4));
		}
		else if (npcId == RIBA_IREN)
		{
			int chance = 1;
			if (callerId == ORFEN)
				chance = 9;
			
			if (callerId != RIBA_IREN && (caller.getCurrentHp() / caller.getMaxHp() < 0.5) && Rnd.get(10) < chance)
			{
				npc.getAI().setIntention(CtrlIntention.IDLE, null, null);
				npc.setTarget(caller);
				npc.doCast(SkillTable.getInstance().getInfo(4516, 1));
			}
		}
		return super.onFactionCall(npc, caller, attacker, isPet);
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		int npcId = npc.getNpcId();
		if (npcId == ORFEN)
		{
			L2GrandBossData data = GrandBossManager.getInstance().getBossData(ORFEN);
			if (data.getState() == ALIVE && (npc.getCurrentHp() - damage) < (npc.getMaxHp() / 2))
			{
				teleport(npc, 0);
				data.setState(TELEPORTED);
				GrandBossManager.getInstance().updateBossData(ORFEN, true);
			}
			else if (Rnd.get(10) < 1 && !npc.isInsideRadius(attacker, 300, false, false) && npc.isInsideRadius(attacker, 1000, false, false))
			{
				npc.broadcastNpcSay(_message[Rnd.get(3)].replace("%s", attacker.getName().toString()));
				attacker.teleToLocation(npc.getX(), npc.getY(), npc.getZ(), 0);
				npc.setTarget(attacker);
				npc.doCast(SkillTable.getInstance().getInfo(4064, 1));
			}
		}
		else if (npcId == RIBA_IREN)
		{
			if (!npc.isCastingNow() && (npc.getCurrentHp() - damage) < (npc.getMaxHp() / 2.0))
			{
				npc.setTarget(attacker);
				npc.doCast(SkillTable.getInstance().getInfo(4516, 1));
			}
		}
		return super.onAttack(npc, attacker, damage, isPet);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		if (npc.getNpcId() == ORFEN)
		{
			cancelQuestTimers("orfen_maintenance");

			int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
			final L2GrandBossData data = GrandBossManager.getInstance().getBossData(ORFEN);
			data.setRespawn(System.currentTimeMillis() + respawnTime);
			GrandBossManager.getInstance().updateBossState(ORFEN, DEAD);
			GrandBossManager.getInstance().cleanClans(ORFEN);
			// TODO: remove players from zone
			
			startQuestTimer("orfen_unlock", respawnTime, null, null, 0);
			startQuestTimer("minion_despawn_all", 20000, null, null, 0);

			npc.broadcastPacket(new PlaySound(1, "BS02_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));	
		}
		return super.onKill(npc, killer, isPet);
	}
	
	// TODO: BOSS
	
	private void teleport(L2Npc npc, int index)
	{
		((L2Attackable) npc).clearAggroList();
		npc.getAI().setIntention(CtrlIntention.IDLE, null, null);

		L2Spawn spawn = npc.getSpawn();
		Location loc = _locs[index];
		spawn.setLocx(loc.getX());
		spawn.setLocy(loc.getY());
		spawn.setLocz(loc.getZ());
		npc.teleToLocation(loc.getX(), loc.getY(), loc.getZ(), 0);
	}
	
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
			GrandBossManager.getInstance().updateBossState(ORFEN, DEAD);
			startQuestTimer("orfen_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		L2GrandBossInstance raid;
		if (data.getState() == DEAD)
		{
			raid = (L2GrandBossInstance) addSpawn(ORFEN, _locs[Rnd.get(3) + 1], false, 0, false);
			data.setInstance(raid);
			data.setState(ALIVE);
			GrandBossManager.getInstance().updateBossData(ORFEN, true);
		}
		else
		{
			raid = (L2GrandBossInstance) addSpawn(ORFEN, data.getLocation(), false, 0, false);
			raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
			data.setInstance(raid);
		}
		startQuestTimer("orfen_maintenance", TIMER_MAINTENANCE, raid, null, TIMER_MAINTENANCE);
		raid.broadcastPacket(new PlaySound(1, "BS01_A", 1, raid.getObjectId(), raid.getX(), raid.getY(), raid.getZ()));
	}
	
	public static void main(String[] args)
	{
		new Orfen(Orfen.class.getSimpleName(), "ai/individual");
	}
}