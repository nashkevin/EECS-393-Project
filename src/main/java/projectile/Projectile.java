package main.java.projectile;

import main.java.agent.Agent;
import main.java.environment.Environment;

import java.awt.geom.Point2D;
import main.java.misc.Vector2D;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class Projectile {
	/** The distance into the environment's edge (in pixels) that the Projectile
	  * can travel before it despawns. */
	private static final int PROJECTILE_LEEWAY = 30;

	private UUID id = UUID.randomUUID();
	private transient Environment environment;
	private Agent owner;
	private Point2D.Double position;
	private Vector2D velocity;
	private int timeToLive;
	private int damage;
	private double size;

	private Timer timer = new Timer("Projectile Timer");

	public Projectile(
		Environment environment, Agent owner, Point2D.Double position,
		Vector2D velocity, int damage, double size
	) {

		this.environment = environment;
		this.owner = owner;
		this.position = position;
		this.velocity = velocity;
		this.timeToLive = (int) (25000 / velocity.getMagnitude());
		this.damage = damage;
		this.size = size;

		// despawn when timeToLive is up
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				despawn(); // despawn the projectile
				timer.cancel(); // terminate the timer
			}
		}, timeToLive);
	}

	public final UUID getID() {
		return id;
	}

	protected final Environment getEnvironment() {
		return environment;
	}

	public final Agent getOwner() {
		return owner;
	}

	public final Point2D.Double getPosition() {
		return (Point2D.Double) position.clone();
	}

	public final Vector2D getVelocity() {
		return new Vector2D(velocity);
	}

	public final void update() {
		double oldX = position.getX();
		double oldY = position.getY();

		double newX = oldX + (velocity.getMagnitude() * Math.cos(velocity.getAngle()));
		double newY = oldY + (velocity.getMagnitude() * -Math.sin(velocity.getAngle()));

		if ((Math.pow(newX, 2) + Math.pow(newY, 2)) >=
				(Math.pow(environment.getRadius() + PROJECTILE_LEEWAY, 2)) ) {
			despawn();
		} else {
			position.setLocation(newX, newY);

			onCollision(environment.checkCollision(this));
		}
	}

	public final void despawn() {
		environment.despawnProjectile(this);
	}

	protected final void onCollision(List<Agent> agents) {
		if (agents != null) {
			for (Agent agent : agents) {
				agent.applyDamage(damage);
				getOwner().awardPoints(damage);
				despawn();
				return;
			}
		}
	}

	public String getHexColor() {
		return getOwner().getHexColor();
	}

	public double getSize() {
		return size;
	}
}
