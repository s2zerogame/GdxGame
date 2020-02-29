package com.gdx.game.entities;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.gdx.game.Enums.ENTITYSTATE;
import com.gdx.game.Enums.ENTITYTYPE;
import com.gdx.game.Media;
import com.gdx.game.box2d.Box2dHelper;
import com.gdx.game.box2d.Box2dWorld;
import com.gdx.game.map.Chunk;
import com.gdx.game.map.Tile;

import java.util.ArrayList;

public class Bird extends Entity {
    private float maxHeight;
    private Tile destTile;
    private TextureRegion tRegion;
    private static final int BIRD_SPEED = 15;

    public Bird(Vector3 pos3, Box2dWorld box2d, ENTITYSTATE state) {
        super(Media.tree, Media.birdShadow, 8, 8);
        this.maxHeight = setHeight();
        this.type = ENTITYTYPE.BIRD;
        this.getPos3().set(getPos3());
        this.body = Box2dHelper.createBody(box2d.getWorld(), getWidth()/2, getHeight()/2, getWidth()/4, 0, pos3, BodyDef.BodyType.StaticBody);
        //this.sensor = Box2dHelper.createSensor(box2d.getWorld(), getWidth(), getHeight()*.85f, getWidth()/2, getHeight()/3, pos3, BodyDef.BodyType.DynamicBody);
        //this.hashcode = sensor.getFixtureList().get(0).hashCode();
        this.state = state;
        this.ticks = true;
    }

    @Override
    public void draw(SpriteBatch batch) {
        setTextureRegion();
        setFlipped();

        batch.draw(Media.birdShadow, getPos3().x, getPos3().y);
        if(tRegion != null){
            batch.draw(tRegion, getPos3().x, getPos3().y + getPos3().z);
        }
    }

    private void setTextureRegion() {
        if(isFlying() || isLanding()) {
            tRegion = Media.birdFlyAnim.getKeyFrame(time, true);
        } else if(isWalking()) {
            tRegion = Media.birdWalkAnim.getKeyFrame(time, true);
        } else if(isFeeding()) {
            tRegion = Media.birdPeckAnim.getKeyFrame(time, true);
        }
    }

    private void setFlipped() {
        if(destVec != null && ((destVec.x > 0 && !tRegion.isFlipX()) || (destVec.x < 0 && tRegion.isFlipX()))) {
            tRegion.flip(true, false);
        }
    }

    @Override
    public void tick(float delta, Chunk chunk) {
        if(isHovering()){
            setLanding();
        } else if(isLanding()){
            land();
        } else if(needsDestination()){
            newDestinationOrHover(chunk);
        } else if(hasDestination()) {
            moveToDestination(delta);
            clearDestination();
        } else if(isNotAirBorn()){
            setNewState(delta);
        }

        if(isFlying()){
            checkFlyHeight();
            toggleHitboxes(false);
        }
    }

    private void toggleHitboxes(boolean b) {
        body.setActive(b);
        //sensor.setActive(b);
    }

    private void setNewState(float delta) {
        if(coolDown > 0){
            coolDown -= delta;
            if(isWalking()){
                walk(delta);
            }
        } else {
            if(MathUtils.randomBoolean(.2f)) {
                state = ENTITYSTATE.FLYING;
            } else if(MathUtils.randomBoolean(.5f)) {
                state = ENTITYSTATE.FEEDING;
                coolDown = .5f;
            } else if(MathUtils.randomBoolean(.3f)) {
                state = ENTITYSTATE.WALKING;
                coolDown = 1f;
            }
        }
    }

    private void clearDestination() {
        if(isAtDestination()) {
            destVec = null;
            destTile = null;
        }
    }

    private void moveToDestination(float delta) {
        body.setTransform(body.getPosition().interpolate(new Vector2(destTile.getPos3().x + getWidth(),
                destTile.getPos3().y + getHeight()), delta * BIRD_SPEED / 4, Interpolation.circle), 0);

        updatePositions();
    }

    private void updatePositions() {
        //sensor.setTransform(body.getPosition(),0);
        getPos3().x = body.getPosition().x - getWidth()/2;
        getPos3().y = body.getPosition().y - getHeight()/4;
    }

    private float setHeight() {
        return MathUtils.random(10) + 10;
    }

    private void checkFlyHeight() {
        if (isNotHigh()) {
            getPos3().z += 0.1;
        }
        if (isTooHigh()) {
            getPos3().z -= 0.1;
        }
    }

    private void land() {
        if (isAirBorn()) {
            getPos3().z -= 0.5;
        }
        if(getPos3().z <= 0) {
            // Landed
            getPos3().z = 0;
            state = ENTITYSTATE.NONE;
            toggleHitboxes(true);
        }
    }

    private void setLanding() {
        if(MathUtils.randomBoolean(.05f)) {
            state = ENTITYSTATE.LANDING;
        }
    }

    private void newDestinationOrHover(Chunk chunk) {
        // 15% chance a new destination is set, unless over water then always
        // get a new destination
        if(MathUtils.randomBoolean(.85f) || currentTile.isWater()) {
            setDestination(chunk);
            maxHeight = setHeight();
        } else {
            state = ENTITYSTATE.HOVERING;
        }
    }

    private void setDestination(Chunk chunk){
        for(ArrayList<Tile> row : chunk.getTiles()) {
            if(destTile != null) {
                break;
            }
            for(Tile tile : row) {
                if (tile.isGrass() && MathUtils.random(100) > 99 && tile != currentTile) {
                    destTile = tile;
                    getVector(destTile.getPos3());
                    break;
                }
            }
        }
    }

    private void walk(float delta) {
        if(currentTile.isPassable()) {
            if(tRegion.isFlipX()) {
                body.setTransform(body.getPosition().x - BIRD_SPEED / 4 * delta, body.getPosition().y,0);
            } else {
                body.setTransform(body.getPosition().x + BIRD_SPEED / 4 * delta, body.getPosition().y,0);
            }
            updatePositions();
        }
    }

    private boolean hasDestination() {
        return destVec != null;
    }

    private boolean isAtDestination() {
        return currentTile.getPos3().epsilonEquals(destTile.getPos3(), 20);
    }

    private boolean needsDestination() {
        return destVec == null && isFlying();
    }

    public boolean isAirBorn(){
        return getPos3().z > 0;
    }

    public boolean isNotAirBorn(){
        return getPos3().z == 0;
    }

    public boolean isHigh(){
        return getPos3().z == maxHeight;
    }

    public boolean isNotHigh(){
        return getPos3().z < maxHeight;
    }

    public boolean isTooHigh(){
        return getPos3().z > maxHeight;
    }

    private boolean isFlying() {
        return state == ENTITYSTATE.FLYING;
    }

    private boolean isHovering(){
        return state == ENTITYSTATE.HOVERING;
    }

    private boolean isLanding(){
        return state == ENTITYSTATE.LANDING;
    }

    private boolean isFeeding(){
        return state == ENTITYSTATE.FEEDING;
    }

    private boolean isWalking(){
        return state == ENTITYSTATE.WALKING;
    }
}