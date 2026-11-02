package com.nexora.os.lead;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@RestController
@RequestMapping("/api/leads")
public class LeadController {
 private final LeadRepository repository;
 public LeadController(LeadRepository repository){this.repository=repository;}
 @GetMapping public List<Lead> all(){return repository.findAll();}
 @PostMapping public ResponseEntity<Lead> create(@Valid @RequestBody Lead lead){lead.id=null;return ResponseEntity.status(201).body(repository.save(lead));}
 @PatchMapping("/{id}/status") public Lead changeStatus(@PathVariable Long id,@RequestBody StatusUpdate update){
   Lead lead=repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
   if(update.status()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
   lead.status=update.status();return repository.save(lead);
 }
 public record StatusUpdate(Lead.Status status) {}
}