export interface Animal {
  name: string;
  sound(): string;
}

export class Dog implements Animal {
  name: string;
  constructor(name: string) {
    this.name = name;
  }
  sound(): string {
    return 'woof';
  }
  fetch(item: Toy): Toy {
    return item;
  }
}

export class Toy {
  label: string;
  constructor(label: string) {
    this.label = label;
  }
}

export type DogBreed = 'labrador' | 'poodle' | 'beagle';
