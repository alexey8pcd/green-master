package ru.alexey_ovcharov.webserver.persist;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
@author Alexey
*/
@Stateless
public class NoteTypesFacade extends AbstractFacade<NoteTypes> {

    @PersistenceContext(unitName = "ru.alexey_ovcharov_webserver_war_1.0PU")
    private EntityManager em;

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public NoteTypesFacade() {
        super(NoteTypes.class);
    }

}