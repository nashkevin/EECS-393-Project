package main.java.agent;

import main.java.environment.Environment;

import java.awt.Point;
import java.util.Random;
import java.util.UUID;

import main.java.misc.Vector2D;
import main.java.projectile.Projectile;
import main.java.projectile.ProjectileFactory;


public abstract class Agent {
	
	public static enum Team {
		ENEMY, RED, BLUE
	}

	private UUID id = UUID.randomUUID();
	private transient Environment environment;
	private Team team;
	private Point position;
	private double rotation;
	private double size;

	/** gameplay attributes */
	private int health;
	private int maxHealth;
	private int movementSpeed;
	private int projectileDamage;
	private int projectileSpeed;
	private double projectileSpread;
	private double fireRate;

	public Agent(Environment environment, Point position, Team team) {
		this.environment = environment;
		this.team = team;
		this.position = new Point(position);
		this.rotation = 0;
	}

	/******************************
	 * start of getters and setters *
	 ******************************/
	public final UUID getID() {
		return id;
	}

	protected final Environment getEnvironment() {
		return environment;
	}

	public final Team getTeam() {
		return team;
	}

	public final void setTeam(Team team) {
		this.team = team;
	}

	public final Point getPosition() {
		return (position == null) ? null : new Point(position);
	}

	public final void setPosition(int x, int y) {
		this.position.setLocation(x, y);
	}

	public final void setPosition(Point p) {
		this.position.setLocation(p);
	}

	public final double getRotation() {
		return rotation;

	}

	public final double getSize() {
		return size;
	}

	public final void setSize(double size) {
		this.size = size;
	}

	public final int getHealth() {
		return health;
	}

	public final void setHealth(int health) {
		this.health = health;

		// if current health exceeds maxHealth, reduce to maxHealth
		this.health = (health > maxHealth) ? maxHealth : health;
	}

	public final int getMaxHealth() {
		return maxHealth;
	}

	public final void setMaxHealth(int maxHealth) {
		this.maxHealth = maxHealth;

		// if current health exceeds maxHealth, reduce to maxHealth
		this.health = (health > maxHealth) ? maxHealth : health;
	}

	public final int movementSpeed() {
		return movementSpeed;
	}

	public final void setMovementSpeed(int movementSpeed) {
		this.movementSpeed = movementSpeed;
	}

	public final int getProjectileDamage() {
		return projectileDamage;
	}

	public final void setProjectileDamage(int projectileDamage) {
		this.projectileDamage = projectileDamage;
	}

	public final int getProjectileSpeed() {
		return projectileSpeed;
	}

	public final void setProjectileSpeed(int projectileSpeed) {
		this.projectileSpeed = projectileSpeed;
	}

	public final double getProjectileSpread() {
		return projectileSpread;
	}

	public final void setProjectileSpread(double projectileSpread) {
		this.projectileSpread = projectileSpread;
	}

	public final double getFireRate() {
		return fireRate;
	}

	public final void setFireRate(double fireRate) {
		this.fireRate = fireRate;
	}
	/******************************
	 * end of getters and setters *
	 ******************************/

	public void update() {
		
	}

	/** reduces health by an amount */
	public final void applyDamage(int amount) {
		health -= amount;
		if (health <= 0) {
			despawn();
		}
	}

	public abstract void despawn();
}
