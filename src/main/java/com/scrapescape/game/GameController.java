package com.scrapescape.game;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import static com.scrapescape.game.GameModels.*;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService service;
    public GameController(GameService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public State create() { return service.create(); }
    @GetMapping("/{id}") public State state(@PathVariable UUID id) { return service.state(id); }
    @GetMapping("/{id}/locations") public List<LocationCard> locations(@PathVariable UUID id) { return service.locations(id); }
    @PostMapping("/{id}/explorations") public ActionResult explore(@PathVariable UUID id,@Valid @RequestBody ExploreRequest request) {
        return service.explore(id,request.locationId());
    }
    @PatchMapping("/{id}/items/{batchId}/storage") public ActionResult move(@PathVariable UUID id,@PathVariable long batchId,@Valid @RequestBody MoveRequest request) {
        return service.move(id,batchId,request.storage());
    }
    @PostMapping("/{id}/items/{batchId}/clean") public ActionResult clean(@PathVariable UUID id,@PathVariable long batchId) { return service.clean(id,batchId); }
    @DeleteMapping("/{id}/items/{batchId}") public ActionResult discard(@PathVariable UUID id,@PathVariable long batchId) { return service.discard(id,batchId); }
    @PostMapping("/{id}/repairs/{partId}") public ActionResult craft(@PathVariable UUID id,@PathVariable long partId) { return service.craft(id,partId); }
    @PostMapping("/{id}/rests") public ActionResult rest(@PathVariable UUID id,@Valid @RequestBody RestRequest request) { return service.rest(id,request.hours()); }
}
