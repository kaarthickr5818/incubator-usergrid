package org.apache.usergrid.rest.test.resource.app;


import java.util.Iterator;
import java.util.List;

import org.apache.usergrid.rest.ApiResponse;
import org.apache.usergrid.rest.RevisedApiResponse;
import org.apache.usergrid.rest.test.resource.CollectionResource;


/**
 * A stateful iterable collection response.  This is a "collection" of entities from our response that are easier
 * to work with. The Generic means that we can type cast the iterator
 *
 * Keep generics? Maybe just use entities for now
 * 1.) Primary key
 * 2.) Default data-> default data is different from type to type. (Groups would need path and title, Activities require actors...etc)
 * 3.) Things that you can do with them-> Groups create connections or something else. Adding users to a group. ( this can be boiled down to creating a connection )
 *
 * Two connecting builder patterns
 * 1. POST /collection/entity/verb (e.g. likes or following)/collection/entity  //connect any two entities
 *  - POST /users/fred/following/users/barney
 * 2. POST /collection/entity/collection/entity //for built in collections e.g. add user to group, add role to group, etc
 *  - POST users/fred/groups/funlovincriminals
 *
 * Two similar builder patterns for getting connected entities
 * 1. GET /users/fred/following
 * 2. GET /users/fred/groups
 *
 */
public class ApiResponseCollection<T> implements Iterable<T>, Iterator<T> {

    private final CollectionResource sourceEndpoint;
    private RevisedApiResponse<T> response;


    public Iterator<T> entities;


    public ApiResponseCollection(final CollectionResource sourceCollection, final RevisedApiResponse response){
        this.response = response;
        this.sourceEndpoint = sourceCollection;
        this.entities = response.getEntities().iterator();
    }

    public RevisedApiResponse<T> getResponse(){
        return response;
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }


    @Override
    public boolean hasNext() {
        if(!entities.hasNext()){
            advance();
        }

        return entities.hasNext();
    }


    @Override
    public T next() {
        return entities.next();
    }


    /**
     * Go back to the endpoint and try to load the next page
     */
    private void advance(){

      //call the original resource for the next page.

        final String cursor = response.getCursor();

        //no next page
        if(cursor == null){
            return;
        }

        response = sourceEndpoint.withCursor( cursor ).getResponse();
        this.entities = response.getEntities().iterator();
    }


    @Override
    public void remove() {
        throw new UnsupportedOperationException( "Remove is unsupported" );
    }
}