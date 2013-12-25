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

import net.sf.l2j.gameserver.GeoData;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager;
import net.sf.l2j.gameserver.instancemanager.GrandBossManager.L2GrandBossData;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.network.serverpackets.Earthquake;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.util.Util;
import net.sf.l2j.util.Rnd;

public class Baium extends AbstractNpcAI
{
	// Monsters
	public static final int BAIUM = 45450;
	private static final int ARCHANGEL = 45451;
	private static final int BAIUM_STONE = 45400;
	
	// States
	public static final byte DEAD = 0;
	public static final byte ASLEEP = 1;
	public static final byte AWAKE = 2;
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours
	private static final int SPAWN_DELAY = 86400000; // 1 day
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_SKILL = 2000; // 2 seconds
	private static final int TIMER_IDLE = 15; // 15 * maintenance
	private static final int TIMER_MINION_AGRO = 10000; // 10 seconds
	
	// Other
	private static final List<L2Attackable> _archangels = new ArrayList<>(5);
	private L2BossZone _zone;
	private long _idle = 0;
	private int _animation = 0;
	private L2PcInstance _waker;
	private L2Character _target;
	private static final Location _locBaium = new Location(116033, 17447, 10104, 40188);
	private static final Location[] _locsArchangels = 
	{
		new Location(114239, 17168, 10080, 63544),
		new Location(115780, 15564, 10080, 13620),
		new Location(114880, 16236, 10080, 5400),
		new Location(115168, 17200, 10080, 0),
		new Location(115792, 16608, 10080, 0),
	};
	
	public Baium(String name, String descr)
	{
		super(name, descr);
		
		addEventId(BAIUM, QuestEventType.ON_ATTACK);
		addEventId(BAIUM, QuestEventType.ON_KILL);
		addEventId(BAIUM, QuestEventType.ON_SPAWN);

		addStartNpc(BAIUM_STONE);
		addTalkId(BAIUM_STONE);
		
		_zone = GrandBossManager.getInstance().getZoneByXYZ(113100, 14500, 10077);
		
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(BAIUM);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("baium_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		// Waking Baium
		// Baium
		if (event.equalsIgnoreCase("baium_maintenance"))
		{
			if (_idle > 0 && --_idle == 0)
			{
				_zone.oustAllPlayers();

				despawnArchangels();
				
				npc.deleteMe();
				addSpawn(BAIUM_STONE, _locBaium, false, 0, false);
				GrandBossManager.getInstance().updateBossState(BAIUM, ASLEEP);
				cancelQuestTimers("baium_maintenance");
			}
			else
				GrandBossManager.getInstance().updateBossData(BAIUM, true);
		}
		else if (event.equalsIgnoreCase("baium_skill"))
		{
			baiumSkill(npc);
		}
		else if (event.equalsIgnoreCase("baium_animation"))
		{
			switch (--_animation)
			{
				case 3: // neck
					npc.broadcastPacket(new SocialAction(npc, 3));
					startQuestTimer("baium_animation", 11000, npc, null, 0);
					break;
				case 2: // sacrifice
					if (_waker != null)
					{
						if (!_waker.isInsideRadius(npc, 300, false, false))
						{
							_waker.teleToLocation(115929, 17349, 10077, 0);
							
							try
							{
								Thread.sleep(2000);
							}
							catch (InterruptedException e)
							{
								e.printStackTrace();
							}
						}
						
						_waker.doDie(npc);
					}
					startQuestTimer("baium_animation", 4000, npc, null, 0);
					break;
				case 1: // roar
					npc.broadcastPacket(new SocialAction(npc, 1));
					spawnArchangels();
					startQuestTimer("baium_animation", 7000, npc, null, 0);
					break;
				case 0: // enable
					npc.setIsInvul(false);
					npc.setIsImmobilized(false);
					npc.setRunning();
					
					_idle = TIMER_IDLE;
					startQuestTimer("baium_maintenance", TIMER_MAINTENANCE, npc, null, TIMER_MAINTENANCE);
					startQuestTimer("baium_skill", TIMER_SKILL, npc, null, TIMER_SKILL);
					break;
			}
		}
		else if (event.equalsIgnoreCase("baium_unlock"))
		{
			trySpawnBoss(GrandBossManager.getInstance().getBossData(BAIUM));
		}
		// Archangels
		else if (event.equalsIgnoreCase("angels_aggro_reconsider"))
		{
			boolean updateTarget = false; // Update or no the target
			
			for (L2Npc minion : _archangels)
			{
				L2Attackable angel = ((L2Attackable) minion);
				if (angel == null)
					continue;
				
				L2Character victim = angel.getMostHated();
				
				if (Rnd.get(100) < 10) // Chaos time
					updateTarget = true;
				else
				{
					if (victim != null) // Target is a unarmed player ; clean aggro.
					{
						if (victim instanceof L2PcInstance && victim.getActiveWeaponInstance() == null)
						{
							angel.stopHating(victim); // Clean the aggro number of previous victim.
							updateTarget = true;
						}
					}
					else
						// No target currently.
						updateTarget = true;
				}
				
				if (updateTarget)
				{
					L2Character newVictim = getRandomTarget(minion);
					if (newVictim != null && victim != newVictim)
					{
						angel.addDamageHate(newVictim, 0, 10000);
						angel.getAI().setIntention(CtrlIntention.ATTACK, newVictim);
					}
				}
			}
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player)
	{
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(BAIUM);
		if (data.getState() == ASLEEP)
		{
			_waker = player;
			npc.deleteMe();

			final L2GrandBossInstance baium = (L2GrandBossInstance) addSpawn(BAIUM, npc, false, 0, true);
			data.setInstance(baium);
			data.setState(AWAKE);
			GrandBossManager.getInstance().updateBossData(BAIUM, true);
			
			baium.setIsInvul(true);
			baium.setIsImmobilized(true);
			_zone.broadcastPacket(new Earthquake(baium.getX(), baium.getY(), baium.getZ(), 40, 10));
			
			_animation = 4;
			baium.broadcastPacket(new SocialAction(baium, 2));
			startQuestTimer("baium_animation", 12000, baium, null, 0);
		}
		return null;
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		npc.disableCoreAI(true);
		return super.onSpawn(npc);
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		if (npc.isInvul())
			return null;
		
		if (attacker.getMountType() == 1)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(4258, 1);
			if (attacker.getFirstEffect(skill) == null)
			{
				npc.setTarget(attacker);
				npc.doCast(skill);
			}
		}
		
		_idle = TIMER_IDLE;

		return super.onAttack(npc, attacker, damage, isPet);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		cancelQuestTimers("baium_maintenance");
		cancelQuestTimers("baium_skill");
		despawnArchangels();
		
		int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
		final L2GrandBossData data = GrandBossManager.getInstance().getBossData(BAIUM);
		data.setRespawn(System.currentTimeMillis() + respawnTime);
		GrandBossManager.getInstance().updateBossState(BAIUM, DEAD);
		GrandBossManager.getInstance().cleanClans(BAIUM);
		// TODO: remove players from zone
		
		startQuestTimer("baium_unlock", respawnTime, null, null, 0);

		npc.broadcastPacket(new PlaySound(1, "BS01_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
				
		return super.onKill(npc, killer, isPet);
	}
	
	/**
	 * This method allows to select a random target, and is used both for Baium and angels.
	 * @param npc to check.
	 * @return the random target.
	 */
	private L2Character getRandomTarget(L2Npc npc)
	{
		int npcId = npc.getNpcId();
		List<L2Character> result = new ArrayList<>();
		
		for (L2Character obj : npc.getKnownList().getKnownType(L2Character.class))
		{
			if (obj instanceof L2PcInstance)
			{
				if (obj.isDead() || !(GeoData.getInstance().canSeeTarget(npc, obj)))
					continue;
				
				if (((L2PcInstance) obj).isGM() && ((L2PcInstance) obj).getAppearance().getInvisible())
 					continue;
 				
				if (npcId == ARCHANGEL && ((L2PcInstance) obj).getActiveWeaponInstance() == null)
					continue;
				
				result.add(obj);
			}
			// Case of Archangels, they can hit Baium.
			else if (obj instanceof L2GrandBossInstance && npcId == ARCHANGEL)
				result.add(obj);
		}
		
		// If there's no players available, Baium and Angels are hitting each other.
		if (result.isEmpty())
		{
			if (npcId == BAIUM) // Case of Baium. Angels should never be without target.
			{
				for (L2Npc minion : _archangels)
					if (minion != null)
						result.add(minion);
			}
		}
		
		return (result.isEmpty()) ? null : result.get(Rnd.get(result.size()));
	}
	
	/**
	 * The personal casting AI for Baium.
	 * @param npc baium, basically...
	 */
	private void baiumSkill(L2Npc npc)
	{
		if (npc.isInvul() || npc.isCastingNow())
			return;
		
		// Pickup a target if no or dead victim. If Baium was hitting an angel, 50% luck he reconsiders his target. 10% luck he decides to reconsiders his target.
		if (_target == null || _target.isDead() || !(npc.getKnownList().knowsObject(_target)) || (_target instanceof L2MonsterInstance && Rnd.get(10) < 5) || Rnd.get(10) == 0)
			_target = getRandomTarget(npc);
		
		// If result is null, return directly.
		if (_target == null)
			return;
		
		final L2Skill skill = SkillTable.getInstance().getInfo(getRandomSkill(npc), 1);
		
		// Adapt the skill range, because Baium is fat.
		if (Util.checkIfInRange(skill.getCastRange() + npc.getCollisionRadius(), npc, _target, true))
		{
			npc.getAI().setIntention(CtrlIntention.IDLE);
			npc.setTarget(skill.getId() == 4135 ? npc : _target);
			npc.doCast(skill);
		}
		else
			npc.getAI().setIntention(CtrlIntention.FOLLOW, _target, null);
	}
	
	/**
	 * Pick a random skill through that list.<br>
	 * If Baium feels surrounded, he will use AoE skills. Same behavior if he is near 2+ angels.<br>
	 * @param npc baium
	 * @return a usable skillId
	 */
	private int getRandomSkill(L2Npc npc)
	{
		// Baium's selfheal. It happens exceptionaly.
		if (npc.getCurrentHp() / npc.getMaxHp() < 0.1)
		{
			if (Rnd.get(10000) == 777) // His lucky day.
				return 4135;
		}
		
		int skill = 4127; // Default attack if nothing is possible.
		final int chance = Rnd.get(100); // Remember, it's 0 to 99, not 1 to 100.
		
		// If Baium feels surrounded or see 2+ angels, he unleashes his wrath upon heads :).
		if (getPlayersCountInRadius(600, npc, false) >= 20 || npc.getKnownList().getKnownTypeInRadius(L2MonsterInstance.class, 600).size() >= 2)
		{
			if (chance < 25)
				skill = 4130;
			else if (chance >= 25 && chance < 50)
				skill = 4131;
			else if (chance >= 50 && chance < 75)
				skill = 4128;
			else if (chance >= 75 && chance < 100)
				skill = 4129;
		}
		else
		{
			if (npc.getCurrentHp() / npc.getMaxHp() > 0.75)
			{
				if (chance < 10)
					skill = 4128;
				else if (chance >= 10 && chance < 20)
					skill = 4129;
			}
			else if (npc.getCurrentHp() / npc.getMaxHp() > 0.5)
			{
				if (chance < 10)
					skill = 4131;
				else if (chance >= 10 && chance < 20)
					skill = 4128;
				else if (chance >= 20 && chance < 30)
					skill = 4129;
			}
			else if (npc.getCurrentHp() / npc.getMaxHp() > 0.25)
			{
				if (chance < 10)
					skill = 4130;
				else if (chance >= 10 && chance < 20)
					skill = 4131;
				else if (chance >= 20 && chance < 30)
					skill = 4128;
				else if (chance >= 30 && chance < 40)
					skill = 4129;
			}
			else
			{
				if (chance < 10)
					skill = 4130;
				else if (chance >= 10 && chance < 20)
					skill = 4131;
				else if (chance >= 20 && chance < 30)
					skill = 4128;
				else if (chance >= 30 && chance < 40)
					skill = 4129;
			}
		}
		return skill;
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
			GrandBossManager.getInstance().updateBossState(BAIUM, DEAD);
			startQuestTimer("baium_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		if (data.getState() == AWAKE)
		{
			L2GrandBossInstance baium = (L2GrandBossInstance) addSpawn(BAIUM, data.getLocation(), false, 0, false);
			baium.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
			baium.setRunning();
			data.setInstance(baium);
			
			_idle = TIMER_IDLE;
			startQuestTimer("baium_maintenance", TIMER_MAINTENANCE, baium, null, TIMER_MAINTENANCE);
			startQuestTimer("baium_skill", TIMER_SKILL, baium, null, TIMER_SKILL);
			// missing sound?
			
			spawnArchangels();
		}
		else
		{
			addSpawn(BAIUM_STONE, _locBaium, false, 0, false);
			if (data.getState() == DEAD)
				GrandBossManager.getInstance().updateBossState(BAIUM, ASLEEP);
		}
	}
	
	// TODO: MINIONS
	
	private void spawnArchangels()
	{
		for (Location loc : _locsArchangels)
		{
			L2Attackable angel = (L2Attackable) addSpawn(ARCHANGEL, loc, false, 0, true);
			angel.setIsRaidMinion(true);
			angel.setRunning();
			_archangels.add(angel);
		}
		
		startQuestTimer("angels_aggro_reconsider", TIMER_MINION_AGRO, null, null, TIMER_MINION_AGRO);
	}
	
	private void despawnArchangels()
	{
		cancelQuestTimer("angels_aggro_reconsider", null, null);
		
		for (L2Attackable minion : _archangels)
		{
			if (minion != null)
				minion.deleteMe();
		}
		_archangels.clear();
	}
	
	public static void main(String[] args)
	{
		new Baium(Baium.class.getSimpleName(), "ai/individual");
	}
}