package main.java.projectile;

import main.java.agent.Agent;
import main.java.environment.Environment;
import main.java.misc.Vector2D;
import main.java.projectile.Projectile;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

public class ProjectileFactory {
	
	private transient Environment environment;
	private Agent owner;

	private int damage;
	private double speed;
	private double spread;
	private int firingDelay; // must wait this duration (in ms) before firing
	private double size;

	private boolean ready;

	/** A random number generator **/
	private static final Random random = new Random();
	private Timer timer = new Timer("ProjectileFactory Timer");

	public ProjectileFactory(Environment environment, Agent owner, int damage,
		double speed, double spread, int firingDelay, double size
	) {
		this.environment = environment;
		this.owner = owner;
		this.damage = damage;
		this.speed = speed;
		this.spread = spread;
		this.firingDelay = firingDelay;
		this.size = size;

		// become ready to fire every time a firingDelay passes
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				setReadyToFire(true);
			}
		}, 0, firingDelay);
	}

	/********************************
	 * start of getters and setters *
	 ********************************/
	public final Agent getOwner() {
		return owner;
	}

	public final void setOwner(Agent owner) {
		this.owner = owner;
	}	

	public final int getDamage() {
		return damage;
	}

	public final void setDamage(int damage) {
		this.damage = damage;
	}

	public final double getSpeed() {
		return speed;
	}

	public final void setSpeed(double speed) {
		this.speed = speed;
	}
 
	public final double getSpread() {
		return spread;
	}

	public final void setSpread(int spread) {
		this.spread = spread;
	}

	public final boolean isReadyToFire() {
		return ready;
	}

	public final void setReadyToFire(boolean b) {
		this.ready = b;
	}

	public final int getFiringDelay() {
		return firingDelay;
	}

	public void setFiringDelay(int delay) {
		if (delay > 0) {
			this.firingDelay = delay;
			
			// terminate timer execution thread
			timer.cancel();
			timer = new Timer();
			
			// assign new delay
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					setReadyToFire(true);
				}
			}, 500, firingDelay);
		} else {
			throw new IllegalArgumentException("firing delay must be positive");
		}
	}
	
	private void resetFiringTimer() {
		setFiringDelay(firingDelay);
	}

	public final double getSize() {
		return size;
	}

	public final void setSize(double size) {
		this.size = size;
	}
	/******************************
	 * end of getters and setters *
	 ******************************/

	public Projectile fireProjectile() {
		return fireProjectile(owner.getAngle());
	}

	public Projectile fireProjectile(double angle) {
		if (isReadyToFire()) {
			resetFiringTimer();
			setReadyToFire(false);
			double offset = random.nextDouble() * spread * 2 - spread;
			Vector2D shotVector = new Vector2D(speed, angle + offset);
			Projectile projectile = new Projectile(environment, owner,
				owner.getPosition(), shotVector, damage, size);
			environment.addProjectile(projectile);
			return projectile;
		}
		return null;
	}
}
