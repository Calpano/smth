<script lang="ts">
  import type { Item } from '../types';

  interface Props {
    item: Item;
    onRemove: () => void;
  }

  let { item, onRemove }: Props = $props();
</script>

<div class="card">
  <span>{item.label}</span>
  <button onclick={onRemove}>Remove</button>
</div>
