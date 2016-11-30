package main.java.agent;

import main.java.environment.Environment;
import main.java.projectile.ProjectileFactory;
import main.java.web.ClientInput;

import java.awt.geom.Point2D;

import java.util.Queue;
import java.util.LinkedList;


public class PlayerAgent extends Agent {

	/** PlayerAgent's speed will not exceed its haste times this multiple */
	private int MAX_SPEED_MULTIPLE = 10;

	private String name = "An Unnamed Hero";
	private int level = 0;
	private int points = 0;
	private int pointsUntilLevelUp;

	Queue<ClientInput> eventInbox;

	public PlayerAgent(
		Environment environment, Point2D.Double position, String name
	) {
		this(environment, position, name, Agent.Team.RED);
	}

	public PlayerAgent(
		Environment environment, Point2D.Double position,
		String name, Agent.Team team
	) {
		super(
			environment,
			position,
			new ProjectileFactory(environment, null, 1, 7,
				Math.toRadians(5.0), 1000, 1.0),
			team,
			1,
			100,
			0.3
		);

		this.name = name;
		this.pointsUntilLevelUp = levelToPoints(level + 1);
		this.eventInbox = new LinkedList<ClientInput>();

		this.getGun().setOwner(this);
	}

	public final String getName() {
		return name;
	}

	public final void setName(String name) {
		this.name = name;
	}

	public final int getLevel() {
		return level;
	}

	public final int getPoints() {
		return points;
	}

	public final int getPointsUntilLevelUp() {
		return pointsUntilLevelUp;
	}

	public void awardPoints(int pointsAwarded) {
		if (pointsAwarded >= pointsUntilLevelUp && level < 100) {
			this.points = pointsAwarded - pointsUntilLevelUp;
			level++; // Level up!
			// This handles being awarded enough points for multiple levels at once
			while (this.points > levelToPoints(level)) {
				this.points -= levelToPoints(level);
				level++;
			}
			upgrade(level);
			getEnvironment().updateEnvironmentLevel();
			pointsUntilLevelUp = levelToPoints(level + 1) - this.points;
		} else {
			this.points += pointsAwarded;
			pointsUntilLevelUp -= pointsAwarded;
		}
	}

	private void upgrade(int level) {
		// Upgrade damage. Min 1, Max 50
		getGun().setDamage((int) Math.round(0.49 * level + 1));

		// Upgrade bullet speed. Min 7, Max 25
		getGun().setSpeed(0.18 * level + 7);

		// Upgrade firing delay. Max 1000, Min 100
		getGun().setFiringDelay(1000 - 9 * level);

		// Upgrade health. Min 100, Max 1000
		double healthRatio = (double) getHealth() / getMaxHealth();
		setMaxHealth(9 * level + 100);
		setHealth((int) Math.round(healthRatio * getMaxHealth()));

		// Upgrade haste. Min 0.3, Max 1
		setHaste(0.007 * level + 0.3);
	}

	/** Returns the number of points needed to reach the given level */
	public static int levelToPoints(int level) {
		return level * level + 100;
	}

	/** Returns the level that would be reached by earning
	 *  the given number of points */
	public static int pointsToLevel(int points) {
		return (points < 100) ? 0 : (int)Math.sqrt(points - 100);
	}

	@Override
	public final void despawn() {
		getEnvironment().despawnPlayerAgent(this);
	}
	
	public void addPlayerEvent(ClientInput event) {
		eventInbox.add(event);
	}

	public Queue<ClientInput> getPlayerEvents() {
		return new LinkedList<ClientInput>(eventInbox);
	}

	@Override
	public void update() {
		/** number of inputs for the left direction */
		int countLeft = 0;
		/** number of inputs for the right direction */
		int countRight = 0;
		/** number of inputs for the up direction */
		int countUp = 0;
		/** number of inputs for the down direction */
		int countDown = 0;

		/** combined horizontal inputs */
		int horizontalInput = 0;
		/** combined vertical inputs */
		int verticalInput = 0;

		while (!eventInbox.isEmpty()) {
			ClientInput event = eventInbox.poll();
			
			if (event.getAngle() != null) {
				setAngle(event.getAngle());
			}

			if (event.isLeft()) {
				countLeft++;
			}
			if (event.isRight()) {
				countRight++;
			}
			if (event.isUp()) {
				countUp++;
			}
			if (event.isDown()) {
				countDown++;
			}
			
			if (event.isFiring()) {
				getGun().fireProjectile();
			}
		}

		horizontalInput = countRight - countLeft;
		verticalInput = countUp - countDown;

		if (verticalInput != 0 || horizontalInput != 0) {
			move(Math.atan2(verticalInput, horizontalInput));
		} else {
			move();
		}
		
	}

	private void move(double inputAngle) {
		// x component of velocity
		double x = getVelocity().getMagnitude() * Math.cos(getVelocity().getAngle());
		// y component of velocity
		double y = getVelocity().getMagnitude() * Math.sin(getVelocity().getAngle());

		// increase velocity componentwise by haste
		x += getHaste() * Math.cos(inputAngle);
		y += getHaste() * Math.sin(inputAngle);

		// update velocity angle following the above increase
		getVelocity().setAngle(Math.atan2(y, x));

		getVelocity().setMagnitude(Math.sqrt(x * x + y * y));

		if (getVelocity().getMagnitude() >= getHaste() * MAX_SPEED_MULTIPLE) {
			getVelocity().setMagnitude(getHaste() * MAX_SPEED_MULTIPLE);
		}

		x = getVelocity().getMagnitude() * Math.cos(getVelocity().getAngle());
		y = getVelocity().getMagnitude() * Math.sin(getVelocity().getAngle());

		x += getPosition().getX();
		y += getPosition().getY();
		setPosition(x, y);
	}

	private void move() {
		getVelocity().setMagnitude(getVelocity().getMagnitude() - getHaste() / 3.0);

		if (getVelocity().getMagnitude() > 0.0) {
			double x = getVelocity().getMagnitude() * Math.cos(getVelocity().getAngle());
			double y = getVelocity().getMagnitude() * Math.sin(getVelocity().getAngle());
			
			x += getPosition().getX();
			y += getPosition().getY();

			setPosition(x, y);
		} else {
			getVelocity().setMagnitude(0.0);
		}
	}
}
