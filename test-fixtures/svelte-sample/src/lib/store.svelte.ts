import type { Item } from './types';

export class ItemStore {
  items: Item[] = $state([]);

  add(item: Item): void {
    this.items.push(item);
  }

  remove(id: number): void {
    this.items = this.items.filter(i => i.id !== id);
  }

  get count(): number {
    return this.items.length;
  }
}
