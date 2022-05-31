using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;

namespace Kiota.Builder;
public class DiscriminatorInformation : ICloneable {
    private ConcurrentDictionary<string, CodeTypeBase> discriminatorMappings = new(StringComparer.OrdinalIgnoreCase);
    /// <summary>
    /// Gets/Sets the discriminator values for the class where the key is the value as represented in the payload.
    /// </summary>
    public IOrderedEnumerable<KeyValuePair<string, CodeTypeBase>> DiscriminatorMappings
    {
        get
        {
            return discriminatorMappings.OrderBy(static x => x.Key);
        }
    }
    /// <summary>
    /// Gets/Sets the name of the property to use for discrimination during deserialization.
    /// </summary>
    public string DiscriminatorPropertyName { get; set; }

    public void AddDiscriminatorMapping(string key, CodeTypeBase type)
    {
        if(type == null) throw new ArgumentNullException(nameof(type));
        if(string.IsNullOrEmpty(key)) throw new ArgumentNullException(nameof(key));
        discriminatorMappings.TryAdd(key, type);
    }

    public CodeTypeBase GetDiscriminatorMappingValue(string key)
    {
        if(string.IsNullOrEmpty(key)) throw new ArgumentNullException(nameof(key));
        if(discriminatorMappings.TryGetValue(key, out var value))
            return value;
        return null;
    }

    public object Clone()
    {
        return new DiscriminatorInformation {
            DiscriminatorPropertyName = DiscriminatorPropertyName,
            discriminatorMappings = discriminatorMappings == null ? null : new (discriminatorMappings)
        };
    }
}
