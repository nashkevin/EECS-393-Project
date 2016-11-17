package main.java.projectile;

import main.java.environment.Environment;

import main.java.agent.Agent;
import java.awt.Point;
import main.java.misc.Vector2D;


public interface FIProjectileCreator {
	public Projectile createProjectile(Environment env, Agent owner,
		Point position, Vector2D velocity);
}
