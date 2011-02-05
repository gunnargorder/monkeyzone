/*
 * Copyright (c) 2009-2011 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.monkeyzone;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.monkeyzone.controls.AIControl;
import com.jme3.monkeyzone.controls.AutonomousCharacterControl;
import com.jme3.monkeyzone.controls.AutonomousControl;
import com.jme3.monkeyzone.controls.AutonomousVehicleControl;
import com.jme3.monkeyzone.controls.CharacterAnimControl;
import com.jme3.monkeyzone.controls.ManualCharacterControl;
import com.jme3.monkeyzone.controls.ManualControl;
import com.jme3.monkeyzone.controls.ManualVehicleControl;
import com.jme3.monkeyzone.controls.SimpleAIControl;
import com.jme3.monkeyzone.messages.AutoControlMessage;
import com.jme3.monkeyzone.messages.ManualControlMessage;
import com.jme3.monkeyzone.messages.ServerAddEntityMessage;
import com.jme3.monkeyzone.messages.ServerAddPlayerMessage;
import com.jme3.monkeyzone.messages.ServerEffectMessage;
import com.jme3.monkeyzone.messages.ServerEnterEntityMessage;
import com.jme3.monkeyzone.messages.ServerEntityDataMessage;
import com.jme3.monkeyzone.messages.ServerRemoveEntityMessage;
import com.jme3.monkeyzone.messages.ServerRemovePlayerMessage;
import com.jme3.network.connection.Client;
import com.jme3.network.connection.Server;
import com.jme3.network.physicssync.PhysicsSyncManager;
import com.jme3.network.physicssync.SyncCharacterMessage;
import com.jme3.network.physicssync.SyncRigidBodyMessage;
import jme3tools.navmesh.NavMesh;
import jme3tools.navmesh.util.NavMeshGenerator;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3tools.optimize.GeometryBatchFactory;

/**
 * Base game entity managing class, stores and loads the entities,
 * used on server and on client. Automatically sends changes via network when
 * running on server, used to apply network data on client and server.
 * @author normenhansen
 */
public class WorldManager {

    private Server server;
    private Client client;
    private long myPlayerId = -1;
    private long myGroupId = -1;
    private NavMesh navMesh = new NavMesh();
    private Node rootNode;
    private Node worldRoot;
    private HashMap<Long, Spatial> entities = new HashMap<Long, Spatial>();
    private int newId = 0;
    private Application app;
    private AssetManager assetManager;
    private NavMeshGenerator generator = new NavMeshGenerator();
    private PhysicsSpace space;
    private List<Control> userControls = new LinkedList<Control>();
    private PhysicsSyncManager syncManager;

    public WorldManager(Application app, Node rootNode, PhysicsSpace space, Server server) {
        this.app = app;
        this.rootNode = rootNode;
        this.assetManager = app.getAssetManager();
        this.space = space;
        this.server = server;
        syncManager = new PhysicsSyncManager(app, server);
        syncManager.setSyncFrequency(Globals.NETWORK_SYNC_FREQUENCY);
        syncManager.addObject(-1, this);
        syncManager.setMessageTypes(AutoControlMessage.class,
                ManualControlMessage.class);
    }

    public WorldManager(Application app, Node rootNode, PhysicsSpace space, Client client) {
        this.app = app;
        this.rootNode = rootNode;
        this.assetManager = app.getAssetManager();
        this.space = space;
        this.client = client;
        syncManager = new PhysicsSyncManager(app, client);
        syncManager.setMaxDelay(Globals.NETWORK_MAX_PHYSICS_DELAY);
        syncManager.addObject(-1, this);
        syncManager.setMessageTypes(AutoControlMessage.class,
                ManualControlMessage.class,
                SyncCharacterMessage.class,
                SyncRigidBodyMessage.class,
                ServerEnterEntityMessage.class,
                ServerAddEntityMessage.class,
                ServerAddPlayerMessage.class,
                ServerEffectMessage.class,
                ServerRemoveEntityMessage.class,
                ServerRemovePlayerMessage.class);
    }

    public boolean isServer() {
        return server != null;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    /**
     * adds a control to the list of controls that are added to the spatial
     * currently controlled by the user (chasecam, ui control etc.)
     * @param control
     */
    public void addUserControl(Control control) {
        userControls.add(control);
    }

    public long getMyPlayerId() {
        return myPlayerId;
    }

    public void setMyPlayerId(long myPlayerId) {
        this.myPlayerId = myPlayerId;
    }

    public long getMyGroupId() {
        return myGroupId;
    }

    public void setMyGroupId(long myGroupId) {
        this.myGroupId = myGroupId;
    }

    /**
     * get the NavMesh of the currently loaded level
     * @return
     */
    public NavMesh getNavMesh() {
        return navMesh;
    }

    /**
     * get the world root node (not necessarily the application rootNode!)
     * @return
     */
    public Node getWorldRoot() {
        return worldRoot;
    }

    public PhysicsSyncManager getSyncManager() {
        return syncManager;
    }

    public PhysicsSpace getPhysicsSpace() {
        return space;
    }

    /**
     * loads the specified level node
     * @param name
     */
    public void loadLevel(String name) {
        worldRoot = (Node) assetManager.loadModel(name);
    }

    /**
     * detaches the level and clears the cache
     */
    public void closeLevel() {
        if (isServer()) {
            for (Iterator<PlayerData> it = PlayerData.getAIPlayers().iterator(); it.hasNext();) {
                PlayerData playerData = it.next();
                removePlayer(playerData.getId());
            }
        }
        for (Iterator<Long> et = new LinkedList(entities.keySet()).iterator(); et.hasNext();) {
            Long entry = et.next();
            syncManager.removeObject(entry);
        }
        entities.clear();
        newId = 0;
        space.removeAll(worldRoot);
        rootNode.detachChild(worldRoot);
        ((DesktopAssetManager) assetManager).clearCache();
    }

    /**
     * preloads the models with the given names
     * @param modelNames
     */
    public void preloadModels(String[] modelNames) {
        for (int i = 0; i < modelNames.length; i++) {
            String string = modelNames[i];
            assetManager.loadModel(string);
        }
    }

    /**
     * creates the nav mesh for the loaded level
     */
    public void createNavMesh() {

        Mesh mesh = new Mesh();

        //version a: from mesh
        GeometryBatchFactory.mergeGeometries(findGeometries(worldRoot, new LinkedList<Geometry>()), mesh);
        Mesh optiMesh = generator.optimize(mesh);

        navMesh.loadFromMesh(optiMesh);

//        Geometry navBaseGeom = new Geometry("NavBaseMesh");
//        navBaseGeom.setMesh(mesh);
//        Material red = new Material(manager, "Common/MatDefs/Misc/WireColor.j3md");
//        red.setColor("Color", ColorRGBA.Red);
//        navBaseGeom.setMaterial(red);
//        rootNode.attachChild(navBaseGeom);

        Geometry navGeom = new Geometry("NavMesh");
        navGeom.setMesh(optiMesh);
        Material green = new Material(assetManager, "Common/MatDefs/Misc/WireColor.j3md");
        green.setColor("Color", ColorRGBA.Green);
        navGeom.setMaterial(green);

        worldRoot.attachChild(navGeom);
    }

    /**
     * attaches the level node to the rootnode
     */
    public void attachLevel() {
        space.addAll(worldRoot);
        rootNode.attachChild(worldRoot);
    }

    private List<Geometry> findGeometries(Node node, List<Geometry> geoms) {
        for (Iterator<Spatial> it = node.getChildren().iterator(); it.hasNext();) {
            Spatial spatial = it.next();
            if (spatial instanceof Geometry) {
                geoms.add((Geometry) spatial);
            } else if (spatial instanceof Node) {
                findGeometries((Node) spatial, geoms);
            }
        }
        return geoms;
    }

    /**
     * adds a new player with new id (used on server only)
     * @param id
     * @param groupId
     * @param name
     * @param aiId
     */
    public long addNewPlayer(int groupId, String name, int aiId) {
        long playerId = PlayerData.getNew(name);
        addPlayer(playerId, groupId, name, aiId);
        return playerId;
    }

    /**
     * adds a player (sends message if server)
     * @param id
     * @param groupId
     * @param name
     * @param aiId
     */
    public void addPlayer(long id, int groupId, String name, int aiId) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Adding player: {0}", id);
        if (isServer()) {
            syncManager.broadcast(new ServerAddPlayerMessage(id, name, groupId, aiId));
        }
        PlayerData player = null;
        player = new PlayerData(id, groupId, name, aiId);
        PlayerData.add(id, player);
    }

    /**
     * removes a player
     * @param id
     */
    public void removePlayer(long id) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Removing player: {0}", id);
        if (isServer()) {
            //TODO: remove other (AI) entities if this is a human client..
            syncManager.broadcast(new ServerRemovePlayerMessage(id));
            long entityId = PlayerData.getLongData(id, "entity_id");
            if (entityId != -1) {
                enterEntity(id, -1);
                //TODO: check if character, removing all entities on logout ^^..
                removeEntity(entityId);
            }
        }
        PlayerData.remove(id);
    }

    /**
     * gets the entity with the specified id
     * @param id
     * @return
     */
    public Spatial getEntity(long id) {
        return entities.get(id);
    }

    /**
     * gets the entity belonging to a PhysicsCollisionObject
     * @param object
     * @return
     */
    public Spatial getEntity(PhysicsCollisionObject object) {
        Object obj = object.getUserObject();
        if (obj instanceof Spatial) {
            Spatial spatial = (Spatial) obj;
            if (entities.containsValue(spatial)) {
                return spatial;
            }
        }
        return null;
    }

    /**
     * finds the entity id of a given spatial if there is one
     * @param entity
     * @return
     */
    public long getEntityId(Spatial entity) {
        for (Iterator<Entry<Long, Spatial>> it = entities.entrySet().iterator(); it.hasNext();) {
            Entry<Long, Spatial> entry = it.next();
            if (entry.getValue() == entity) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * gets the entity belonging to a PhysicsCollisionObject
     * @param object
     * @return
     */
    public long getEntityId(PhysicsCollisionObject object) {
        Object obj = object.getUserObject();
        if (obj instanceof Spatial) {
            Spatial spatial = (Spatial) obj;
            if (spatial != null) {
                return getEntityId(spatial);
            }
        }
        return -1;
    }

    /**
     * adds a new entity (only used on server)
     * @param modelIdentifier
     * @param location
     * @param rotation
     * @return
     */
    public long addNewEntity(String modelIdentifier, Vector3f location, Quaternion rotation) {
//        long id = 0;
//        while (entities.containsKey(id)) {
//            id++;
//        }
        newId++;
        addEntity(newId, modelIdentifier, location, rotation);
        return newId;
    }

    /**
     * add an entity (vehicle, immobile house etc), always related to a spatial
     * with specific userdata like hp, maxhp etc. (sends message if server)
     * @param id
     * @param modelIdentifier
     * @param location
     * @param rotation
     */
    public void addEntity(long id, String modelIdentifier, Vector3f location, Quaternion rotation) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Adding entity: {0}", id);
        if (isServer()) {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Broadcast adding entity: {0}", id);
            syncManager.broadcast(new ServerAddEntityMessage(id, modelIdentifier, location, rotation));
        }
        Node entityModel = (Node) assetManager.loadModel(modelIdentifier);
        if (entityModel.getControl(RigidBodyControl.class) != null) {
            entityModel.getControl(RigidBodyControl.class).setPhysicsLocation(location);
            entityModel.getControl(RigidBodyControl.class).setPhysicsRotation(rotation.toRotationMatrix());
            syncManager.addObject(id, entityModel.getControl(RigidBodyControl.class));
        } else if (entityModel.getControl(CharacterControl.class) != null) {
            entityModel.getControl(CharacterControl.class).setPhysicsLocation(location);
            entityModel.getControl(CharacterControl.class).setViewDirection(rotation.mult(Vector3f.UNIT_Z).multLocal(1, 0, 1).normalizeLocal());
            entityModel.addControl(new CharacterAnimControl());
            syncManager.addObject(id, entityModel.getControl(CharacterControl.class));
        } else if (entityModel.getControl(VehicleControl.class) != null) {
            entityModel.getControl(VehicleControl.class).setPhysicsLocation(location);
            entityModel.getControl(VehicleControl.class).setPhysicsRotation(rotation.toRotationMatrix());
            syncManager.addObject(id, entityModel.getControl(VehicleControl.class));
        } else {
            entityModel.setLocalTranslation(location);
            entityModel.setLocalRotation(rotation);
        }
        entityModel.setUserData("player_id", -1l);
        entityModel.setUserData("group_id", -1);
        entities.put(id, entityModel);
        space.addAll(entityModel);
        worldRoot.attachChild(entityModel);
    }

    /**
     * removes the entity with the specified id (sends message if server)
     * @param id
     */
    public void removeEntity(long id) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Removing entity: {0}", id);
        if (isServer()) {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Broadcast removing entity: {0}", id);
            syncManager.broadcast(new ServerRemoveEntityMessage(id));
        }
        syncManager.removeObject(id);
        Spatial spat = entities.remove(id);
        if (spat == null) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "try removing entity thats not there: {0}", id);
            return;
        }
        spat.removeFromParent();
        space.removeAll(spat);
    }

    /**
     * handle player entering entity (sends message if server)
     * @param playerId
     * @param entityId
     */
    public void enterEntity(long playerId, long entityId) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Player {0} entering entity {1}", new Object[]{playerId, entityId});
        if (isServer()) {
            syncManager.broadcast(new ServerEnterEntityMessage(playerId, entityId));
        }
        long curEntity = PlayerData.getLongData(playerId, "entity_id");
        int groupId = PlayerData.getIntData(playerId, "group_id");
        if (curEntity != -1) {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Player {0} exiting current entity {1}", new Object[]{playerId, curEntity});
            Spatial curEntitySpat = getEntity(curEntity);
            curEntitySpat.setUserData("player_id", -1l);
            curEntitySpat.setUserData("group_id", -1);
            removeTransientControls(curEntitySpat);
            removeAIControl(curEntitySpat);
            if (playerId == myPlayerId) {
                removeUserControls(curEntitySpat);
            }
        }
        PlayerData.setData(playerId, "entity_id", entityId);
        //id -1 means enter no entity
        if (entityId != -1) {
            Spatial spat = getEntity(entityId);
            spat.setUserData("player_id", playerId);
            spat.setUserData("group_id", groupId);
            if (PlayerData.isHuman(playerId)) {
                if (groupId == getMyGroupId()) { //only true on clients
                    makeManualControl(entityId, client);
                    //move controls for local user to new spatial
                    if (playerId == getMyPlayerId()) {
                        addUserControls(spat);
                    }
                } else {
                    makeManualControl(entityId, null);
                }
            } else {
                if (groupId == getMyGroupId()) { //only true on clients
                    makeAutoControl(entityId, client);
                    addAIControl(playerId, spat);
                } else {
                    makeAutoControl(entityId, null);
                }
            }
        }
    }

    /**
     * makes the specified entity ready to be manually controlled by adding
     * a ManualControl based on the entity type (vehicle etc)
     */
    private void makeManualControl(long entityId, Client client) {
        Spatial spat = getEntity(entityId);
        if (spat.getControl(CharacterControl.class) != null) {
            if (client != null) {
                //add net sending for users own manual control
                //TODO: using group id as client id
                if ((Integer) spat.getUserData("group_id") == myGroupId) {
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make human client type manual control for entity {0} ", entityId);
                    spat.addControl(new ManualCharacterControl(client, entityId));
                } else {
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make client type manual control for entity {0} ", entityId);
                    spat.addControl(new ManualCharacterControl());
                }
            } else if (server != null) {
                Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make server type manual control for entity {0} ", entityId);
                spat.addControl(new ManualCharacterControl(syncManager, entityId));
            } else {
                Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make client type manual control for entity {0} ", entityId);
                spat.addControl(new ManualCharacterControl());
            }
        } else if (spat.getControl(VehicleControl.class) != null) {
            if (client != null) {
                //TODO: using group id as client id
                if ((Integer) spat.getUserData("group_id") == myGroupId) {
                    spat.addControl(new ManualVehicleControl(client, entityId));
                } else {
                    spat.addControl(new ManualVehicleControl());
                }
            } else if (server != null) {
                Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make server type manual control for entity {0} ", entityId);
                spat.addControl(new ManualVehicleControl(syncManager, entityId));
            } else {
                Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Make client type manual control for entity {0} ", entityId);
                spat.addControl(new ManualVehicleControl());
            }
        }
    }

    /**
     * makes the specified entity ready to be controlled by an AIControl
     * by adding an AutonomousControl based on entity type.
     */
    private void makeAutoControl(long entityId, Client client) {
        Spatial spat = getEntity(entityId);
        if (spat.getControl(CharacterControl.class) != null) {
            if (client != null) {
                spat.addControl(new AutonomousCharacterControl(client, entityId));
            } else if (server != null) {
                spat.addControl(new AutonomousCharacterControl(syncManager, entityId));
            } else {
                spat.addControl(new AutonomousCharacterControl());
            }
        } else if (spat.getControl(VehicleControl.class) != null) {
            if (client != null) {
                spat.addControl(new AutonomousVehicleControl(client, entityId));
            } else if (server != null) {
                spat.addControl(new AutonomousVehicleControl(syncManager, entityId));
            } else {
                spat.addControl(new AutonomousCharacterControl());
            }
        }
    }

    /**
     * removes all movement controls (ManualControl / AutonomousControl) from
     * spatial
     * @param spat
     */
    private void removeTransientControls(Spatial spat) {
        ManualControl manualControl = spat.getControl(ManualControl.class);
        if (manualControl != null) {
            spat.removeControl(manualControl);
        }
        AutonomousControl autoControl = spat.getControl(AutonomousControl.class);
        if (autoControl != null) {
            spat.removeControl(autoControl);
        }
    }

    /**
     * adds the user controls for human user to the spatial
     */
    private void addUserControls(Spatial spat) {
        for (Iterator<Control> it = userControls.iterator(); it.hasNext();) {
            Control control = it.next();
            spat.addControl(control);
        }
    }

    /**
     * removes the user controls for human user to the spatial
     */
    private void removeUserControls(Spatial spat) {
        for (Iterator<Control> it = userControls.iterator(); it.hasNext();) {
            Control control = it.next();
            spat.removeControl(control);
        }
    }

    /**
     * adds the user controls for human user to the spatial
     */
    private void addAIControl(long playerId, Spatial spat) {
        spat.addControl(new SimpleAIControl(this));
    }

    /**
     * adds the user controls for human user to the spatial
     */
    private void removeAIControl(Spatial spat) {
        AIControl aiControl = spat.getControl(AIControl.class);
        if (aiControl != null) {
            spat.removeControl(aiControl);
        }
    }

    /**
     * set user data of specified entity (sends message if server)
     * @param id
     * @param name
     * @param data
     */
    public void setEntityUserData(long id, String name, Object data) {
        if(isServer()){
//            syncManager.broadcast(new ServerEntityDataMessage());
        }
        getEntity(id).setUserData(name, data);
    }

    /**
     * play animation on specified entity
     * @param entityId
     * @param animationName
     * @param channel
     */
    public void playEntityAnimation(long entityId, String animationName, int channel) {
    }

    public void playWorldEffect(String effectName, Vector3f location, float time) {
        Quaternion rotation = new Quaternion();
        playWorldEffect(-1, effectName, location, rotation, location, rotation, time);
    }

    public void playWorldEffect(String effectName, Vector3f location, Quaternion rotation, float time) {
        playWorldEffect(-1, effectName, location, rotation, location, rotation, time);
    }

    public void playWorldEffect(long id, String effectName, Vector3f location, Quaternion rotation, float time) {
        playWorldEffect(id, effectName, location, rotation, location, rotation, time);
    }

    public void playWorldEffect(long id, String effectName, Vector3f location, Quaternion rotation, Vector3f endLocation, Quaternion endRotation, float time) {
        syncManager.broadcast(new ServerEffectMessage(id, effectName, location, rotation, endLocation, endRotation, time));
    }

    public void update(float tpf) {
        syncManager.update(tpf);
    }
}