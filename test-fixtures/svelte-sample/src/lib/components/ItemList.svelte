<script lang="ts">
  import type { Item } from '../types';
  import { ItemStore } from '../store.svelte';
  import ItemCard from './ItemCard.svelte';

  interface Props {
    store: ItemStore;
    title?: string;
  }

  let { store, title = 'Items' }: Props = $props();
  let search = $state('');

  let filtered = $derived(
    store.items.filter(i => i.label.toLowerCase().includes(search.toLowerCase()))
  );
</script>

<h2>{title}</h2>
<input bind:value={search} placeholder="Search..." />
{#each filtered as item}
  <ItemCard {item} onRemove={() => store.remove(item.id)} />
{/each}
