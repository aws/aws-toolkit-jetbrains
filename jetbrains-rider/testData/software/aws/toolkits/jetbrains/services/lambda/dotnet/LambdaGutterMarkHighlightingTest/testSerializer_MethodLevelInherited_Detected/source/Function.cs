using System.IO;
using Amazon.Lambda.Core;

namespace HelloWorld
{
    public class Function
    {
        [LambdaSerializer(typeof(MyCustomSerializer))]
        public Product DescribeProduct(DescribeProductRequest request)
        {
            var catalogService = new CatalogService();
            return catalogService.DescribeProduct(request.Id);
        }
    }

    public class DescribeProductRequest
    {
        public int Id { get; set; }
    }

    public class Product
    {
        public int Id { get; set; }
    }

    public class CatalogService
    {
        public Product DescribeProduct(int id)
        {
            return new Product { Id = id };
        }
    }

    public class MyCustomSerializer : ILambdaSerializer
    {
        public T Deserialize<T>(Stream requestStream)
        {
            throw new System.NotImplementedException();
        }

        public void Serialize<T>(T response, Stream responseStream)
        {
            throw new System.NotImplementedException();
        }
    }
}