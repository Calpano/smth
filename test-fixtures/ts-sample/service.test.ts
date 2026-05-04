import { describe, it, expect } from 'vitest';
import { DogService } from './service';
import { Dog, Toy } from './models';

describe('DogService', () => {
  it('adds a dog', () => {
    const svc = new DogService();
    svc.add(new Dog('Rex'));
    expect(svc.findByName('Rex')).toBeDefined();
  });

  it('plays fetch', () => {
    const svc = new DogService();
    const dog = new Dog('Rex');
    const toy = new Toy('ball');
    expect(svc.playFetch(dog, toy).label).toBe('ball');
  });
});
