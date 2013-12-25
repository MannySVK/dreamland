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
import net.sf.l2j.gameserver.model.L2CharPosition;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.L2Playable;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PetInstance;
import net.sf.l2j.gameserver.model.quest.QuestEventType;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.SpecialCamera;
import net.sf.l2j.gameserver.util.Util;
import net.sf.l2j.util.Rnd;

public class Antharas extends AbstractNpcAI
{
	public static Antharas _instance;
	
	// Monsters
	public static final int ANTHARAS = 45550;
	private static final int[] DRAGONS = 
	{
		45551,
		45552,
		45553,
		45554,
		45555,
		45556,
		45557,
		45558
	};
	
	// States
	public static final byte DEAD = 0; // Antharas has been killed. Entry is locked.
	public static final byte IDLE = 1; // Antharas is spawned and no one has entered yet. Entry is unlocked.
	public static final byte WAITING = 2; // Antharas is spawned and someone has entered, triggering a 30 minute window for additional people to enter. Entry is unlocked.
	public static final byte ALIVE = 3; // Antharas is engaged in battle, annihilating his foes. Entry is locked.
	
	// Respawn
	private static final int SPAWN_INTERVAL = 259200000; // 3 days
	private static final int SPAWN_WINDOW = 10800000; // 3 hours
	private static final int SPAWN_DELAY = 86400000; // 1 day
	private static final int SPAWN_WAIT = 300000; // 5 minutes
	
	// Timers
	private static final int TIMER_MAINTENANCE = 60000; // 1 minute
	private static final int TIMER_SKILL = 20000; // 20 seconds
	private static final int TIMER_IDLE = 15; // 15 * maintenance
	private static final int TIMER_MINION = 600000; // 10 minutes
	private static final int TIMER_MINION_AGRO = 10000; // 10 seconds
	
	// Other
	private final List<L2Attackable> _minions = new ArrayList<>(10);
	private static final Location _loc = new Location(181323, 114850, -7623, 32768);
	private static L2BossZone _zone;
	private long _idle = 0;
	private int _animation = 0;
	private static final int ANIMATION = 10;
	private L2Playable _target;
	
	private static final int[] ANTHARAS_REGULAR_SKILLS =
	{
		4106,
		4107,
		4108,
		4109,
		4112,
		4113
	};
	private static final int[] ANTHARAS_AOE_SKILLS =
	{
		4106,
		4107,
		4108,
		4109,
		4111
	};
	private static final int[] ANTHARAS_LOWHP_SKILLS =
	{
		4106,
		4107,
		4109,
		4110,
		4111
	};
	
	public Antharas(String name, String descr)
	{
		super(name, descr);
		
		addEventId(ANTHARAS, QuestEventType.ON_SPAWN);
		addEventId(ANTHARAS, QuestEventType.ON_ATTACK);
		addEventId(ANTHARAS, QuestEventType.ON_KILL);
		registerMobs(DRAGONS, QuestEventType.ON_KILL);
		
		_zone = GrandBossManager.getInstance().getZoneByXYZ(177600, 114900, -7709);
		
		L2GrandBossData data = GrandBossManager.getInstance().getBossData(ANTHARAS);
		if (data.getState() == DEAD)
		{
			long temp = data.getRespawn() - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("antharas_unlock", temp, null, null, 0);
			else
				trySpawnBoss(data);
		}
		else
			spawnBoss(data);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		// Regeneration && inactivity task
		if (event.equalsIgnoreCase("antharas_maintenance"))
		{
			if (_idle > 0 && --_idle == 0)
			{
				_zone.oustAllPlayers();
				
				npc.deleteMe();
				GrandBossManager.getInstance().updateBossState(ANTHARAS, IDLE);
				cancelQuestTimers("antharas_maintenance");
				cancelQuestTimers("antharas_skill");
				cancelQuestTimers("minions_spawn");
				cancelQuestTimers("minions_aggro_reconsider");
				
				despawnMinions();

				return null;
			}
			else
				GrandBossManager.getInstance().updateBossData(ANTHARAS, true);
		}
		else if (event.equalsIgnoreCase("antharas_open"))
		{
			if (GrandBossManager.getInstance().getBossData(ANTHARAS).getState() == IDLE)
			{
				GrandBossManager.getInstance().updateBossState(ANTHARAS, WAITING);
				startQuestTimer("antharas_spawn", SPAWN_WAIT, null, null, 0);
			}
		}
		else if (event.equalsIgnoreCase("antharas_spawn"))
		{
			L2GrandBossData data = GrandBossManager.getInstance().getBossData(ANTHARAS);
			final L2GrandBossInstance raid = (L2GrandBossInstance) addSpawn(ANTHARAS, _loc, false, 0, false);
			data.setInstance(raid);
			GrandBossManager.getInstance().updateBossState(ANTHARAS, ALIVE);
			
			raid.setIsInvul(true);
			raid.setIsImmobilized(true);
			
			_zone.broadcastPacket(new PlaySound(1, "B03_A", 0, 0, 0, 0, 0));
			
			_animation = ANIMATION;
			startQuestTimer("antharas_animation", 1000, raid, null, 0);			
		}
		else if (event.equalsIgnoreCase("antharas_animation"))
		{
			switch (_animation)
			{
				// Spawn
				case ANIMATION:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1700, -10, 0, 0, 6500, 0, 0, 1, 0));
					startQuestTimer("antharas_animation", 6000, npc, null, 0);
					_animation--;
					break;
				case 9:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 2100, 10, 0, 0, 9000, 0, 0, 1, 0));
					_zone.broadcastPacket(new SocialAction(npc, 3));
					startQuestTimer("antharas_animation", 8500, npc, null, 0);
					_animation--;
					break;
				case 8:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 4000, 0, 0, 0, 9000, 0, 0, 1, 0));
					_zone.broadcastPacket(new SocialAction(npc, 2));
					startQuestTimer("antharas_animation", 8500, npc, null, 0);
					_animation--;
					break;
				case 7:
					_zone.broadcastPacket(new SpecialCamera(npc.getObjectId(), 1500, 0, 0, 300, 3000, 0, 0, 1, 0));
					startQuestTimer("antharas_enable", 1500, npc, null, 0);
					_animation = 0;
					break;
			}
		}
		else if (event.equalsIgnoreCase("antharas_enable"))
		{
			npc.setIsInvul(false);
			npc.setIsImmobilized(false);
			npc.setRunning();
			
			_idle = TIMER_IDLE;
			startQuestTimer("antharas_maintenance", TIMER_MAINTENANCE, null, null, TIMER_MAINTENANCE);
			startQuestTimer("antharas_skill", 1000, npc, null, TIMER_SKILL);
			startQuestTimer("minions_spawn", TIMER_MINION, npc, null, TIMER_MINION);
			startQuestTimer("minions_aggro_reconsider", TIMER_MINION_AGRO, null, null, TIMER_MINION_AGRO);
		}
		else if (event.equalsIgnoreCase("antharas_skill"))
		{
			antharasSkill(npc);
		}
		else if (event.equalsIgnoreCase("antharas_unlock"))
		{
			final L2GrandBossInstance antharas = (L2GrandBossInstance) addSpawn(ANTHARAS, 181323, 114850, -7623, 32768, false, 0, false);
			GrandBossManager.getInstance().getBossData(ANTHARAS).setInstance(antharas);
			GrandBossManager.getInstance().updateBossState(ANTHARAS, IDLE);
		}
		// minions
		else if (event.equalsIgnoreCase("minions_spawn"))
		{
			if (_idle == TIMER_IDLE)
				spawnMinions(npc);
		}
		else if (event.equalsIgnoreCase("minions_aggro_reconsider"))
		{
			for (L2Attackable monster : _minions)
			{
				if (Rnd.get(3) < 0)
				{
					L2Character victim = monster.getMostHated();
					monster.stopHating(victim);
					L2Playable trg = getRandomTarget(monster);
					if (trg != null)
					{
						monster.addDamageHate(trg, 0, 10000);
						monster.getAI().setIntention(CtrlIntention.ATTACK, trg);
					}
				}
			}
		}
		else if (event.equalsIgnoreCase("minion_destruct"))
		{
			if (npc.getNpcId() == 45551)
				npc.doCast(SkillTable.getInstance().getInfo(5094, 1));
			else
				npc.doCast(SkillTable.getInstance().getInfo(5097, 1));
			_minions.remove(npc);
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		npc.disableCoreAI(true);
		return null;
	}
	
	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isPet)
	{
		if (npc.isInvul())
			return null;
		
		if (attacker.getMountType() == 1)
		{
			final L2Skill skill = SkillTable.getInstance().getInfo(4258, 1);
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
		if (npc.getNpcId() == ANTHARAS)
		{
			cancelQuestTimers("antharas_maintenance");
			cancelQuestTimers("antharas_skill");
			cancelQuestTimers("minions_spawn");
			cancelQuestTimers("minions_aggro_reconsider");
			
			int respawnTime = SPAWN_INTERVAL + Rnd.get(SPAWN_WINDOW);
			L2GrandBossData data = GrandBossManager.getInstance().getBossData(ANTHARAS);
			data.setRespawn(System.currentTimeMillis() + respawnTime);
			data.setState(DEAD);
			GrandBossManager.getInstance().updateBossState(ANTHARAS, DEAD);
			
			startQuestTimer("antharas_unlock", respawnTime, null, null, 0);

			despawnMinions();
			
			_zone.broadcastPacket(new PlaySound(1, "BS01_D", 1, npc.getObjectId(), npc.getX(), npc.getY(), npc.getZ()));
		}
		else
		{
			cancelQuestTimer("minion_destruct", npc, null);
			_minions.remove(npc);
		}
		
		return super.onKill(npc, killer, isPet);
	}
	
	private void antharasSkill(L2Npc npc)
	{
		if (npc.isInvul() || npc.isCastingNow())
			return;
		
		// Pickup a target if no or dead victim. 10% luck he decides to reconsiders his target.
		if (_target == null || _target.isDead() || !(npc.getKnownList().knowsObject(_target)) || Rnd.get(10) == 0)
			_target = getRandomTarget(npc);
		
		// If result is still null, Antharas will roam. Don't go deeper in skill AI.
		if (_target == null)
		{
			if (Rnd.get(10) == 0)
			{
				int x = npc.getX();
				int y = npc.getY();
				int z = npc.getZ();
				
				int posX = x + Rnd.get(-1400, 1400);
				int posY = y + Rnd.get(-1400, 1400);
				
				if (GeoData.getInstance().canMoveFromToTarget(x, y, z, posX, posY, z))
					npc.getAI().setIntention(CtrlIntention.MOVE_TO, new L2CharPosition(posX, posY, z, 0));
			}
			return;
		}
		
		final L2Skill skill = SkillTable.getInstance().getInfo(getRandomSkill(npc), 1);
		
		// Cast the skill or follow the target.
		if (Util.checkIfInRange((skill.getCastRange() < 600) ? 600 : skill.getCastRange(), npc, _target, true))
		{
			npc.getAI().setIntention(CtrlIntention.IDLE);
			npc.setIsCastingNow(true);
			npc.setTarget(_target);
			npc.doCast(skill);
		}
		else
		{
			npc.getAI().setIntention(CtrlIntention.FOLLOW, _target, null);
			npc.setIsCastingNow(false);
		}
	}
	
	/**
	 * Pick a random skill.<br>
	 * Antharas will mostly use utility skills. If Antharas feels surrounded, he will use AoE skills.<br>
	 * Lower than 50% HPs, he will begin to use LOWHP skills.
	 * @param npc Antharas
	 * @return a usable skillId
	 */
	private int getRandomSkill(L2Npc npc)
	{
		final int hpRatio = (int) (npc.getCurrentHp() / npc.getMaxHp() * 100);
		
		// Antharas will use mass spells if he feels surrounded.
		if (getPlayersCountInRadius(1200, npc, false) >= 20)
			return ANTHARAS_AOE_SKILLS[Rnd.get(5)];
		
		if (hpRatio > 50)
			return ANTHARAS_REGULAR_SKILLS[Rnd.get(2)];
		
		return ANTHARAS_LOWHP_SKILLS[Rnd.get(5)];
	}
	
	/**
	 * This method allows to select a random target, and is used both for Antharas and dragons.
	 * @param npc to check.
	 * @return the random target.
	 */
	private L2Playable getRandomTarget(L2Npc npc)
	{
		List<L2Playable> result = new ArrayList<>();
		
		for (L2Character obj : npc.getKnownList().getKnownType(L2Character.class))
		{
			if (obj instanceof L2PetInstance)
				continue;
			
			if (!obj.isDead() && obj instanceof L2Playable)
				result.add((L2Playable) obj);
		}
		
		return (result.isEmpty()) ? null : result.get(Rnd.get(result.size()));
	}
	
	// TODO: RAID
	
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
			GrandBossManager.getInstance().updateBossState(ANTHARAS, DEAD);
			startQuestTimer("antharas_unlock", respawn - System.currentTimeMillis(), null, null, 0);
		}
		else
			spawnBoss(data);
	}
	
	private void spawnBoss(L2GrandBossData data)
	{
		switch (data.getState())
		{
			case DEAD:
				GrandBossManager.getInstance().updateBossState(ANTHARAS, IDLE);
			case IDLE:
				break;
			case WAITING:
				startQuestTimer("antharas_spawn", SPAWN_WAIT, null, null, 0);
				break;
			case ALIVE:
				final L2GrandBossInstance raid = (L2GrandBossInstance) addSpawn(ANTHARAS, data.getLocation(), false, 0, false);
				raid.setCurrentHpMp(data.getCurrentHp(), data.getCurrentMp());
				raid.setRunning();
				data.setInstance(raid);
				
				_idle = TIMER_IDLE;
				startQuestTimer("antharas_maintenance", TIMER_MAINTENANCE, null, null, TIMER_MAINTENANCE);
				startQuestTimer("antharas_skill", TIMER_SKILL, raid, null, TIMER_SKILL);
				startQuestTimer("minions_spawn", TIMER_MINION, raid, null, TIMER_MINION);
				startQuestTimer("minions_aggro_reconsider", TIMER_MINION_AGRO, null, null, TIMER_MINION_AGRO);
				break;
		}
	}
	
	// TODO: MINIONS
	
	private void spawnMinions(L2Npc raid)
	{
		boolean isBehemoth = Rnd.get(100) < 20;
		
		for (int i = 0; i < (isBehemoth ? 1 : 2); i++)
		{
			if (_minions.size() > 9)
				break;
			
			final int npcId = isBehemoth ? 45551 : Rnd.get(45552, 45558);
			final int x = Rnd.get(2) < 1 ? Rnd.get(-300, -100) : Rnd.get(100, 300);
			final int y = Rnd.get(2) < 1 ? Rnd.get(-300, -100) : Rnd.get(100, 300);
			final L2Attackable minion = (L2Attackable) addSpawn(npcId, raid.getX() + x, raid.getY() + y, raid.getZ(), 0, false, 0, true);
			minion.setIsRaidMinion(true);
			minion.setRunning();
			
			_minions.add(minion);
			
			final L2Playable target = getRandomTarget(minion);
			if (target != null)
			{
				minion.addDamageHate(target, 0, 10000);
				minion.getAI().setIntention(CtrlIntention.ATTACK, target);
			}
			
			if (!isBehemoth)
				startQuestTimer("minion_destruct", (TIMER_MINION / 3), minion, null, 0);
		}
	}
	
	private void despawnMinions()
	{
		for (L2Attackable mob : _minions)
		{
			cancelQuestTimer("minion_destruct", mob, null);
			mob.getSpawn().stopRespawn();
			mob.deleteMe();
		}
		_minions.clear();
	}
	
	public static void main(String[] args)
	{
		_instance = new Antharas(Antharas.class.getSimpleName(), "ai.individual");
	}
}