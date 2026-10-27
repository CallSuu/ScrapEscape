package com.scrapescape.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import static com.scrapescape.game.GameModels.*;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService service;
    private final IdempotencyService requests;
    public GameController(GameService service,IdempotencyService requests){this.service=service;this.requests=requests;}
    @PostMapping
    public ResponseEntity<JsonNode> create(@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return response(requests.execute(key,"POST /api/games",null,service::create),HttpStatus.CREATED);
    }
    @GetMapping("/{id}") public State state(@PathVariable UUID id){return service.state(id);}
    @GetMapping("/{id}/screen") public GameScreen screen(@PathVariable UUID id){return service.screen(id);}
    @GetMapping("/{id}/locations") public List<LocationCard> locations(@PathVariable UUID id){return service.locations(id);}
    @PostMapping("/{id}/explorations")
    public ResponseEntity<JsonNode> explore(@PathVariable UUID id,@Valid @RequestBody ExploreRequest request,
                                            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/explorations",request,()->service.explore(id,request.locationId()));
    }
    @PatchMapping("/{id}/items/{batchId}/storage")
    public ResponseEntity<JsonNode> move(@PathVariable UUID id,@PathVariable long batchId,@Valid @RequestBody MoveRequest request,
                                         @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"PATCH /api/games/"+id+"/items/"+batchId+"/storage",request,()->service.move(id,batchId,request.storage()));
    }
    @PatchMapping("/{id}/items/storage")
    public ResponseEntity<JsonNode> moveMany(@PathVariable UUID id,@Valid @RequestBody MoveManyRequest request,
                                             @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"PATCH /api/games/"+id+"/items/storage",request,()->service.moveMany(id,request.batchIds(),request.storage()));
    }
    @PostMapping("/{id}/items/clean")
    public ResponseEntity<JsonNode> cleanMany(@PathVariable UUID id,@Valid @RequestBody BatchItemsRequest request,
                                              @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/items/clean",request,()->service.cleanMany(id,request.batchIds()));
    }
    @DeleteMapping("/{id}/items")
    public ResponseEntity<JsonNode> discardMany(@PathVariable UUID id,@Valid @RequestBody BatchItemsRequest request,
                                                @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"DELETE /api/games/"+id+"/items",request,()->service.discardMany(id,request.batchIds()));
    }
    @PostMapping("/{id}/items/{batchId}/clean")
    public ResponseEntity<JsonNode> clean(@PathVariable UUID id,@PathVariable long batchId,
                                          @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/items/"+batchId+"/clean",null,()->service.clean(id,batchId));
    }
    @DeleteMapping("/{id}/items/{batchId}")
    public ResponseEntity<JsonNode> discard(@PathVariable UUID id,@PathVariable long batchId,
                                            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"DELETE /api/games/"+id+"/items/"+batchId,null,()->service.discard(id,batchId));
    }
    @PostMapping("/{id}/repairs/{partId}")
    public ResponseEntity<JsonNode> craft(@PathVariable UUID id,@PathVariable long partId,
                                          @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/repairs/"+partId,null,()->service.craft(id,partId));
    }
    @PostMapping("/{id}/rests")
    public ResponseEntity<JsonNode> rest(@PathVariable UUID id,@Valid @RequestBody RestRequest request,
                                         @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/rests",request,()->service.rest(id,request.hours()));
    }
    @PostMapping("/{id}/treatments")
    public ResponseEntity<JsonNode> treat(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/treatments",null,()->service.treat(id));
    }
    @PostMapping("/{id}/escape")
    public ResponseEntity<JsonNode> escape(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/escape",null,()->service.escape(id));
    }
    @GetMapping("/{id}/secret-rooms") public List<SecretRoomCard> secretRooms(@PathVariable UUID id){return service.secretRooms(id);}
    @GetMapping("/{id}/secret-rooms/{locationId}") public SecretRoomCard secretRoom(@PathVariable UUID id,@PathVariable long locationId){return service.secretRoom(id,locationId);}
    @PostMapping("/{id}/secret-rooms/{locationId}/defer")
    public ResponseEntity<JsonNode> deferRoom(@PathVariable UUID id,@PathVariable long locationId,
                                              @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/secret-rooms/"+locationId+"/defer",null,()->service.deferRoom(id,locationId));
    }
    @PostMapping("/{id}/secret-rooms/{locationId}/open")
    public ResponseEntity<JsonNode> openRoom(@PathVariable UUID id,@PathVariable long locationId,@Valid @RequestBody OpenRoomRequest request,
                                             @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return action(key,"POST /api/games/"+id+"/secret-rooms/"+locationId+"/open",request,()->service.openRoom(id,locationId,request.mode()));
    }
    private ResponseEntity<JsonNode> action(String key,String operation,Object payload,Supplier<ActionResult> action) {
        return response(requests.execute(key,operation,payload,action),HttpStatus.OK);
    }
    private ResponseEntity<JsonNode> response(IdempotencyService.Result result,HttpStatus status) {
        return ResponseEntity.status(status).header("Idempotency-Replayed",Boolean.toString(result.replayed())).body(result.body());
    }
}
