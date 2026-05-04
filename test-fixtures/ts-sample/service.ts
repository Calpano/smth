import { Dog, Toy } from './models';

export class DogService {
  private dogs: Dog[] = [];

  add(dog: Dog): void {
    this.dogs.push(dog);
  }

  findByName(name: string): Dog | undefined {
    return this.dogs.find(d => d.name === name);
  }

  playFetch(dog: Dog, toy: Toy): Toy {
    return dog.fetch(toy);
  }
}
