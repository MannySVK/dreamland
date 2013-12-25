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

import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.skills.SkillHolder;
import net.sf.l2j.util.Rnd;

/**
 * Queen Ant AI
 * @author Emperorc
 */
public class QueenAnt extends AbstractNpcAI
{
	// Monsters
	private static final int QUEEN = 45250;
	private static final int NURSE = 45251;
	private static final int ROYAL = 45252;
	private static final int LARVA = 45253;
	private static final int[] MINIONS =
	{
		ROYAL, ROYAL, ROYAL, ROYAL, ROYAL, ROYAL, ROYAL, 
		NURSE, NURSE, NURSE, NURSE, NURSE, 
	};
	
	// States
	private static final byte DEAD = 0; // Queen Ant has been killed.
	private static final byte ALIVE = 1; // Queen Ant is spawned.
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours 
	private static final int SPAWN_DELAY = 86400000; // 1 day
	private static final Location _loc = new Location(-21610, 181594, -5734);
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_MINION_HEAL = 1000; // 1 seconds
	
	// Other
	private final static List<L2MonsterInstance> _minions = new ArrayList<>();
	private L2GrandBossInstance _queen = null;
	private L2MonsterInstance _larva = null;
	private static L2BossZone _zone;
	private static SkillHolder HEAL1 = new SkillHolder(4020, 1);
	private static SkillHolder HEAL2 = new SkillHolder(4024, 1);
	
	public QueenAnt(String name, String descr)
	{
		super(name, descr);
		
		final int[] mobs =
		{
			LARVA,
			NURSE,
			ROYAL
		};
		registerMobs(mobs, QuestEventType.ON_SPAWN, QuestEventType.ON_KILL);
		addEventId(QUEEN, QuestEventType.ON_KILL);
		addEventId(LARVA, QuestEventType.ON_FACTION_CALL);
		
		_zone = GrandBossManager.getInstance().getZoneByXYZ(-21610, 181594, -5734);
		
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(QUEEN);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("queen_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		final L2MonsterInstance mob = (L2MonsterInstance) npc;
		switch (npc.getNpcId())
		{
			case LARVA:
				mob.setIsRaidMinion(true);
				mob.setIsImmobilized(true);
				mob.setIsMortal(false);
				break;
			case NURSE:
//				mob.disableCoreAI(true);
			case ROYAL:
				mob.setIsRaidMinion(true);
				_minions.add(mob);
				break;
		}
		return super.onSpawn(npc);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		// Boss
		if (event.equalsIgnoreCase("queen_maintenance"))
		{
			GrandBossManager.getInstance().updateBossData(QUEEN, true);
			
			if (Rnd.get(2) < 1)
				npc.broadcastPacket(new SocialAction(npc, 3));
			else
				npc.broadcastPacket(new SocialAction(npc, 4));
		}
		else if (event.equalsIgnoreCase("queen_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(QUEEN));
		}
		// Minion
		else if (event.equalsIgnoreCase("minion_heal"))
		{
			final boolean larvaNeedHeal = _larva != null && _larva.getCurrentHp() < _larva.getMaxHp();
			final boolean queenNeedHeal = _queen != null && _queen.getCurrentHp() < _queen.getMaxHp();
			for (L2MonsterInstance minion : _minions)
			{
				if (minion == null || minion.getNpcId() != NURSE || minion.isDead() || minion.isCastingNow())
					continue;
				
				final boolean notCasting = minion.getAI().getIntention() != CtrlIntention.CAST;
				if (larvaNeedHeal)
				{
					if (minion.getTarget() != _larva)
						minion.setTarget(_larva);
					
					if (notCasting)
						useMagic(minion, Rnd.nextBoolean() ? HEAL1.getSkill() : HEAL2.getSkill(), _larva);
					
					continue;
				}
				else if (queenNeedHeal)
				{
					if (minion.getLeader() == _larva) // skip larva's minions
						continue;
					
					if (minion.getTarget() != _queen)
						minion.setTarget(_queen);
					
					if (notCasting && Rnd.get(2) < 1)
						useMagic(minion, HEAL1.getSkill(), _queen);
					
					continue;
				}
				
				if (notCasting && minion.getTarget() != null)
					minion.setTarget(null);
			}
		}
		else if (event.equalsIgnoreCase("minion_spawn"))
		{
			spawnMinion(npc.getNpcId(), _queen);
		}
		else if (event.equalsIgnoreCase("minion_despawn_all"))
		{
			despawnMinions();
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onFactionCall(L2Npc npc, L2Npc caller, L2PcInstance attacker, boolean isPet)
	{
		if (caller == null || npc == null)
			return super.onFactionCall(npc, caller, attacker, isPet);
		
		if (caller.getCurrentHp() < caller.getMaxHp())
		{
			npc.setTarget(caller);
			useMagic(npc, HEAL1.getSkill(), caller);
		}
		return null;
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		int npcId = npc.getNpcId();
		if (npcId == QUEEN)
		{
			cancelQuestTimers("queen_maintenance");
			cancelQuestTimers("minion_heal");
			
			int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
			final L2GrandBossData data = GrandBossManager.getInstance().getBossData(QUEEN);
			data.setRespawn(System.currentTimeMillis() + respawnTime);
			GrandBossManager.getInstance().updateBossState(QUEEN, DEAD);
			GrandBossManager.getInstance().cleanClans(QUEEN);
			// TODO: remove players from zone
			
			startQuestTimer("queen_unlock", respawnTime, null, null, 0);
			startQuestTimer("minion_despawn_all", 20000, null, null, 0);

			npc.broadcastPacket(new PlaySound(1, "BS02_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
			
			_minions.clear();
			_larva.deleteMe();
			_larva = null;
			_queen = null;
		}
		else if (npcId == ROYAL)
		{
			L2MonsterInstance mob = (L2MonsterInstance) npc;
			_minions.remove(mob);
			startQuestTimer("minion_spawn", 280000 + Rnd.get(40000), npc, null, 0);
		}
		else if (npcId == NURSE)
		{
			L2MonsterInstance mob = (L2MonsterInstance) npc;
			_minions.remove(mob);
			startQuestTimer("minion_spawn", 10000, npc, null, 0);
		}
		return super.onKill(npc, killer, isPet);
	}
	
	// Raid
	
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
			GrandBossManager.getInstance().updateBossState(QUEEN, DEAD);
			startQuestTimer("queen_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		L2GrandBossInstance raid;
		if (data.getState() == DEAD)
		{
			raid = (L2GrandBossInstance) addSpawn(QUEEN, _loc, false, 0, false);
			data.setInstance(raid);
			data.setState(ALIVE);
			GrandBossManager.getInstance().updateBossData(QUEEN, true);
		}
		else
		{
			Location loc = data.getLocation();
			if (_zone.isInsideZone(loc.getX(), loc.getY(), loc.getZ()))
				raid = (L2GrandBossInstance) addSpawn(QUEEN, loc, false, 0, false);
			else
				raid = (L2GrandBossInstance) addSpawn(QUEEN, _loc, false, 0, false);
			raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
			data.setInstance(raid);
		}
		startQuestTimer("queen_maintenance", TIMER_MAINTENANCE, raid, null, TIMER_MAINTENANCE);
		raid.broadcastPacket(new PlaySound(1, "BS01_A", 1, raid.getObjectId(), raid.getX(), raid.getY(), raid.getZ()));
		
		_queen = raid;
		_larva = (L2MonsterInstance) addSpawn(LARVA, -21600, 179482, -5846, Rnd.get(65536), false, 0, false);
		startQuestTimer("minion_heal", TIMER_MINION_HEAL, null, null, TIMER_MINION_HEAL);

		spawnMinions(raid);

		// Teleport players out of raid zone
		if (Rnd.get(100) < 33)
			_zone.movePlayersTo(-19480, 187344, -5600);
		else if (Rnd.get(100) < 50)
			_zone.movePlayersTo(-17928, 180912, -5520);
		else
			_zone.movePlayersTo(-23808, 182368, -5600);	
	}
	
	// Minion
	
	private void spawnMinions(L2GrandBossInstance raid)
	{
		for (int minion : MINIONS)
			spawnMinion(minion, raid);
	}
	
	private void spawnMinion(int npcId, L2GrandBossInstance raid)
	{
		int x = Rnd.get(2) < 1 ? Rnd.get(80, 150) : Rnd.get(-150, -80);
		int y = Rnd.get(2) < 1 ? Rnd.get(80, 150) : Rnd.get(-150, -80);
		addSpawn(npcId, raid.getX() + x, raid.getY() + y, raid.getZ(), 0, false, 0, false);
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
	
	public void useMagic(L2Npc npc, L2Skill skill, L2Npc target)
	{
		if (npc.isCastingNow())
			return;
		
		if (npc.isSkillDisabled(skill))
			return;
		
		if (npc.getCurrentMp() < npc.getStat().getMpConsume(skill) + npc.getStat().getMpInitialConsume(skill))
			return;
		
		if (npc.isMuted() && skill.isMagic())
			return;
		
		if (npc.isPhysicalMuted() && !skill.isMagic())
			return;
		
		npc.getAI().setIntention(CtrlIntention.CAST, skill, target);
	}
	
	public static void main(String[] args)
	{
		new QueenAnt(QueenAnt.class.getSimpleName(), "ai/individual");
	}
}